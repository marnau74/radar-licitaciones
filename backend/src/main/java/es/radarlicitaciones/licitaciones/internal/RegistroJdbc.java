package es.radarlicitaciones.licitaciones.internal;

import es.radarlicitaciones.licitaciones.AnulacionLeida;
import es.radarlicitaciones.licitaciones.LicitacionLeida;
import es.radarlicitaciones.licitaciones.OrganoLeido;
import es.radarlicitaciones.licitaciones.RegistroDeLicitaciones;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda las versiones con SQL por lotes: un mes de la Plataforma son cientos de miles de versiones y por JPA serían
 * horas. Cada llamada son unas pocas sentencias por lote, sea cual sea su tamaño.
 *
 * <ol>
 *   <li>De cada licitación del lote se queda la versión más reciente.
 *   <li>Se leen las versiones guardadas de esas licitaciones y se descartan las recibidas que no son más nuevas.
 *   <li>Se guardan los órganos y luego las licitaciones ({@code ON CONFLICT ... WHERE} más nueva: aunque dos procesos
 *       escribieran a la vez, nunca se pisa una versión con otra anterior).
 *   <li>Se rehacen los lotes y resultados de las licitaciones que han cambiado.
 *   <li>Se guardan las anulaciones y se marcan las licitaciones afectadas.
 * </ol>
 */
@Repository
class RegistroJdbc implements RegistroDeLicitaciones {

    private static final String GUARDAR_ORGANO = """
            INSERT INTO organo (clave, nombre, nif, dir3, id_plataforma, tipo, ciudad, codigo_postal, web,
                                perfil_contratante, jerarquia, actualizado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (clave) DO UPDATE SET
                nombre = EXCLUDED.nombre, nif = EXCLUDED.nif, dir3 = EXCLUDED.dir3,
                id_plataforma = EXCLUDED.id_plataforma, tipo = EXCLUDED.tipo, ciudad = EXCLUDED.ciudad,
                codigo_postal = EXCLUDED.codigo_postal, web = EXCLUDED.web,
                perfil_contratante = EXCLUDED.perfil_contratante, jerarquia = EXCLUDED.jerarquia,
                actualizado_en = EXCLUDED.actualizado_en
            WHERE organo.actualizado_en <= EXCLUDED.actualizado_en
            """;

    // El texto de búsqueda se calcula en la base de datos con la configuración radar_es (sin tildes y por raíces):
    // título y expediente pesan más (A), luego el órgano (B), luego lotes y nombres de los CPV (C) y por último los
    // adjudicatarios (D).
    private static final String GUARDAR_LICITACION = """
            INSERT INTO licitacion (id, expediente, titulo, url, estado, tipo_contrato, subtipo_contrato, procedimiento,
                                    organo_id, importe_sin_iva, importe_con_iva, valor_estimado, cpv, cpv_prefijos,
                                    nuts, nuts_prefijos, lugar, plazo_presentacion, fecha_publicacion, duracion,
                                    financiacion_ue, num_lotes, documentos, actualizada_en, vista_por_primera_vez,
                                    ingerida_en, anulada, busqueda)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, now(), now(),
                    EXISTS (SELECT 1 FROM anulacion a WHERE a.licitacion_id = ? AND a.cuando >= ?),
                    setweight(to_tsvector('radar_es', ?), 'A')
                    || setweight(to_tsvector('radar_es', ?), 'B')
                    || setweight(to_tsvector('radar_es', ? || ' ' || coalesce(
                           (SELECT string_agg(c.nombre, ' ') FROM cat_cpv c WHERE c.codigo = ANY (?)), '')), 'C')
                    || setweight(to_tsvector('radar_es', ?), 'D'))
            ON CONFLICT (id) DO UPDATE SET
                expediente = EXCLUDED.expediente, titulo = EXCLUDED.titulo, url = EXCLUDED.url,
                estado = EXCLUDED.estado, tipo_contrato = EXCLUDED.tipo_contrato,
                subtipo_contrato = EXCLUDED.subtipo_contrato, procedimiento = EXCLUDED.procedimiento,
                organo_id = EXCLUDED.organo_id, importe_sin_iva = EXCLUDED.importe_sin_iva,
                importe_con_iva = EXCLUDED.importe_con_iva, valor_estimado = EXCLUDED.valor_estimado,
                cpv = EXCLUDED.cpv, cpv_prefijos = EXCLUDED.cpv_prefijos, nuts = EXCLUDED.nuts,
                nuts_prefijos = EXCLUDED.nuts_prefijos, lugar = EXCLUDED.lugar,
                plazo_presentacion = EXCLUDED.plazo_presentacion, fecha_publicacion = EXCLUDED.fecha_publicacion,
                duracion = EXCLUDED.duracion, financiacion_ue = EXCLUDED.financiacion_ue,
                num_lotes = EXCLUDED.num_lotes, documentos = EXCLUDED.documentos,
                actualizada_en = EXCLUDED.actualizada_en, ingerida_en = EXCLUDED.ingerida_en,
                anulada = EXCLUDED.anulada, busqueda = EXCLUDED.busqueda
            WHERE licitacion.actualizada_en < EXCLUDED.actualizada_en
            """;

    private static final String GUARDAR_LOTE =
            "INSERT INTO lote (licitacion_id, numero, objeto, importe_sin_iva, cpv, nuts) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String GUARDAR_RESULTADO = """
            INSERT INTO resultado (licitacion_id, lote, codigo, fecha_adjudicacion, ofertas_recibidas, adjudicatario,
                                   adjudicatario_nif, importe_sin_iva, importe_con_iva, pyme)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String GUARDAR_ANULACION = """
            INSERT INTO anulacion (licitacion_id, cuando) VALUES (?, ?)
            ON CONFLICT (licitacion_id) DO UPDATE SET cuando = GREATEST(anulacion.cuando, EXCLUDED.cuando)
            """;

    private final JdbcTemplate jdbc;
    private final JdbcClient cliente;
    private final JsonMapper json;

    RegistroJdbc(JdbcTemplate jdbc, JdbcClient cliente, JsonMapper json) {
        this.jdbc = jdbc;
        this.cliente = cliente;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoDelGuardado guardar(List<LicitacionLeida> versiones, List<AnulacionLeida> anulaciones) {
        var recibidas = masRecientes(versiones, LicitacionLeida::id, LicitacionLeida::actualizadaEn);
        var guardadas = versionesGuardadas(recibidas.keySet());

        var nuevas = 0;
        var actualizadas = 0;
        var aGuardar = new ArrayList<LicitacionLeida>();
        for (var version : recibidas.values()) {
            var guardada = guardadas.get(version.id());
            if (guardada == null) {
                nuevas++;
                aGuardar.add(version);
            } else if (guardada.isBefore(version.actualizadaEn())) {
                actualizadas++;
                aGuardar.add(version);
            }
        }

        if (!aGuardar.isEmpty()) {
            var organos = guardarOrganos(aGuardar);
            var guardadasAhora = guardarLicitaciones(aGuardar, organos);
            guardarLotesYResultados(guardadasAhora);
        }
        var recibidasAnuladas = masRecientes(anulaciones, AnulacionLeida::id, AnulacionLeida::cuando);
        guardarAnulaciones(recibidasAnuladas.values());

        return new ResultadoDelGuardado(
                nuevas, actualizadas, versiones.size() - nuevas - actualizadas, recibidasAnuladas.size());
    }

    private static <T> Map<Long, T> masRecientes(
            List<T> elementos,
            java.util.function.ToLongFunction<T> id,
            java.util.function.Function<T, OffsetDateTime> momento) {
        BinaryOperator<T> masReciente = (a, b) -> momento.apply(b).isAfter(momento.apply(a)) ? b : a;
        return elementos.stream()
                .sorted(Comparator.comparingLong(id))
                .collect(Collectors.toMap(id::applyAsLong, e -> e, masReciente, LinkedHashMap::new));
    }

    private Map<Long, OffsetDateTime> versionesGuardadas(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var guardadas = new HashMap<Long, OffsetDateTime>();
        cliente.sql("SELECT id, actualizada_en FROM licitacion WHERE id = ANY (?)")
                .param(ids.toArray(Long[]::new))
                .query(rs -> {
                    guardadas.put(rs.getLong(1), rs.getObject(2, OffsetDateTime.class));
                });
        return guardadas;
    }

    /** Guarda los órganos (con los datos de la versión más reciente de cada uno) y devuelve su id por clave. */
    private Map<String, Long> guardarOrganos(List<LicitacionLeida> versiones) {
        var porClave = new LinkedHashMap<String, LicitacionLeida>();
        for (var version : versiones) {
            porClave.merge(
                    version.organo().clave(),
                    version,
                    (a, b) -> b.actualizadaEn().isAfter(a.actualizadaEn()) ? b : a);
        }
        var lista = porClave.values().stream()
                .sorted(Comparator.comparing(v -> v.organo().clave()))
                .toList();
        jdbc.batchUpdate(GUARDAR_ORGANO, lista, lista.size(), (ps, version) -> {
            OrganoLeido o = version.organo();
            var i = 0;
            ps.setString(++i, o.clave());
            ps.setString(++i, o.nombre());
            ps.setString(++i, o.nif());
            ps.setString(++i, o.dir3());
            ps.setString(++i, o.idPlataforma());
            ps.setString(++i, o.tipo());
            ps.setString(++i, o.ciudad());
            ps.setString(++i, o.codigoPostal());
            ps.setString(++i, o.web());
            ps.setString(++i, o.perfilContratante());
            ps.setArray(++i, texto(ps, o.jerarquia()));
            ps.setObject(++i, version.actualizadaEn());
        });
        var ids = new HashMap<String, Long>();
        cliente.sql("SELECT clave, id FROM organo WHERE clave = ANY (?)")
                .param(porClave.keySet().toArray(String[]::new))
                .query(rs -> {
                    ids.put(rs.getString(1), rs.getLong(2));
                });
        return ids;
    }

    /** Devuelve las versiones que de verdad se han escrito (el {@code WHERE} del upsert puede descartar alguna). */
    private List<LicitacionLeida> guardarLicitaciones(List<LicitacionLeida> versiones, Map<String, Long> organos) {
        var filas = jdbc.batchUpdate(GUARDAR_LICITACION, versiones, versiones.size(), (ps, l) -> {
            var i = 0;
            ps.setLong(++i, l.id());
            ps.setString(++i, l.expediente());
            ps.setString(++i, l.titulo());
            ps.setString(++i, l.url());
            ps.setString(++i, l.estado());
            ps.setString(++i, l.tipoContrato());
            ps.setString(++i, l.subtipoContrato());
            ps.setString(++i, l.procedimiento());
            ps.setLong(++i, organos.get(l.organo().clave()));
            ps.setBigDecimal(++i, l.importeSinIva());
            ps.setBigDecimal(++i, l.importeConIva());
            ps.setBigDecimal(++i, l.valorEstimado());
            ps.setArray(++i, texto(ps, l.cpv()));
            ps.setArray(++i, texto(ps, Prefijos.deCpv(l.cpv())));
            ps.setString(++i, l.nuts());
            ps.setArray(++i, texto(ps, Prefijos.deNuts(l.nuts())));
            ps.setString(++i, l.lugar());
            ps.setObject(++i, l.plazoPresentacion(), Types.TIMESTAMP_WITH_TIMEZONE);
            ps.setObject(++i, l.fechaPublicacion(), Types.DATE);
            ps.setString(++i, l.duracion());
            ps.setBoolean(++i, l.financiacionUe());
            ps.setInt(++i, l.lotes().size());
            ps.setString(++i, json.writeValueAsString(l.documentos()));
            ps.setObject(++i, l.actualizadaEn());
            ps.setLong(++i, l.id());
            ps.setObject(++i, l.actualizadaEn());
            ps.setString(++i, l.titulo() + " " + l.expediente());
            ps.setString(++i, l.organo().nombre());
            ps.setString(++i, textoDeLotes(l));
            ps.setArray(++i, texto(ps, l.cpv()));
            ps.setString(++i, adjudicatarios(l));
        });
        var escritas = new ArrayList<LicitacionLeida>();
        var posicion = 0;
        for (var tanda : filas) {
            for (var cambiadas : tanda) {
                if (cambiadas != 0) {
                    escritas.add(versiones.get(posicion));
                }
                posicion++;
            }
        }
        return escritas;
    }

    private static String textoDeLotes(LicitacionLeida licitacion) {
        return licitacion.lotes().stream()
                .map(LicitacionLeida.LoteLeido::objeto)
                .filter(o -> o != null)
                .collect(Collectors.joining(" "));
    }

    private static String adjudicatarios(LicitacionLeida licitacion) {
        return licitacion.resultados().stream()
                .map(LicitacionLeida.ResultadoLeido::adjudicatario)
                .filter(a -> a != null)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    private void guardarLotesYResultados(List<LicitacionLeida> versiones) {
        if (versiones.isEmpty()) {
            return;
        }
        var ids = versiones.stream().map(LicitacionLeida::id).toArray(Long[]::new);
        cliente.sql("DELETE FROM lote WHERE licitacion_id = ANY (?)").param(ids).update();
        cliente.sql("DELETE FROM resultado WHERE licitacion_id = ANY (?)")
                .param(ids)
                .update();

        record Lote(long licitacion, LicitacionLeida.LoteLeido lote) {}
        record Resultado(long licitacion, LicitacionLeida.ResultadoLeido resultado) {}
        var lotes = versiones.stream()
                .flatMap(v -> v.lotes().stream().map(l -> new Lote(v.id(), l)))
                .toList();
        var resultados = versiones.stream()
                .flatMap(v -> v.resultados().stream().map(r -> new Resultado(v.id(), r)))
                .toList();

        jdbc.batchUpdate(GUARDAR_LOTE, lotes, Math.max(lotes.size(), 1), (ps, l) -> {
            ps.setLong(1, l.licitacion());
            ps.setString(2, l.lote().numero());
            ps.setString(3, l.lote().objeto());
            ps.setBigDecimal(4, l.lote().importeSinIva());
            ps.setArray(5, texto(ps, l.lote().cpv()));
            ps.setString(6, l.lote().nuts());
        });
        jdbc.batchUpdate(GUARDAR_RESULTADO, resultados, Math.max(resultados.size(), 1), (ps, r) -> {
            var resultado = r.resultado();
            ps.setLong(1, r.licitacion());
            ps.setString(2, resultado.lote());
            ps.setString(3, resultado.codigo());
            ps.setObject(4, resultado.fechaAdjudicacion(), Types.DATE);
            ps.setObject(5, resultado.ofertasRecibidas(), Types.INTEGER);
            ps.setString(6, resultado.adjudicatario());
            ps.setString(7, resultado.adjudicatarioNif());
            ps.setBigDecimal(8, resultado.importeSinIva());
            ps.setBigDecimal(9, resultado.importeConIva());
            ps.setObject(10, resultado.pyme(), Types.BOOLEAN);
        });
    }

    private void guardarAnulaciones(Collection<AnulacionLeida> anulaciones) {
        if (anulaciones.isEmpty()) {
            return;
        }
        var lista = List.copyOf(anulaciones);
        jdbc.batchUpdate(GUARDAR_ANULACION, lista, lista.size(), (ps, a) -> {
            ps.setLong(1, a.id());
            ps.setObject(2, a.cuando());
        });
        // Una anulación solo cuenta si es posterior a la versión guardada: si la Plataforma vuelve a publicar la
        // licitación después, deja de estar anulada.
        cliente.sql("""
                        UPDATE licitacion l SET anulada = true
                        FROM anulacion a
                        WHERE a.licitacion_id = l.id AND l.id = ANY (?) AND a.cuando >= l.actualizada_en
                          AND NOT l.anulada
                        """)
                .param(lista.stream().map(AnulacionLeida::id).toArray(Long[]::new))
                .update();
    }

    private static java.sql.Array texto(PreparedStatement ps, List<String> valores) throws SQLException {
        return ps.getConnection().createArrayOf("text", valores.toArray(String[]::new));
    }
}
