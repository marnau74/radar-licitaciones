package es.radarlicitaciones.licitaciones.internal;

import es.radarlicitaciones.compartido.Pagina;
import es.radarlicitaciones.compartido.PeticionNoValida;
import es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones;
import es.radarlicitaciones.licitaciones.FiltroDeBusqueda;
import es.radarlicitaciones.licitaciones.LicitacionResumen;
import es.radarlicitaciones.licitaciones.OrdenDeBusqueda;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Búsqueda con SQL generado a partir del filtro. Solo se añaden las condiciones de los criterios que vienen, y todos
 * los valores van como parámetros: nada del usuario se concatena en el SQL.
 */
@Repository
@Transactional(readOnly = true)
class BuscadorJdbc implements BuscadorDeLicitaciones {

    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private static final String COLUMNAS = """
            l.id, l.expediente, l.titulo, l.estado, l.tipo_contrato, l.procedimiento, l.organo_id, o.nombre AS organo,
            l.importe_sin_iva, l.plazo_presentacion, l.fecha_publicacion, l.nuts, l.lugar, l.cpv, l.num_lotes,
            l.financiacion_ue, l.anulada
            """;

    private final JdbcClient jdbc;
    private final Clock reloj;

    BuscadorJdbc(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public Pagina<LicitacionResumen> buscar(FiltroDeBusqueda filtro, OrdenDeBusqueda orden, int pagina, int tamano) {
        if (pagina < 1 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new PeticionNoValida("Página o tamaño de página fuera de rango");
        }
        if ((long) (pagina - 1) * tamano >= RESULTADOS_PAGINABLES) {
            throw new PeticionNoValida(
                    "Solo se pueden recorrer los primeros " + RESULTADOS_PAGINABLES + " resultados: afina la búsqueda");
        }
        var consulta = new Condiciones(filtro, OffsetDateTime.now(reloj));
        var sql = "SELECT " + COLUMNAS
                + ", count(*) OVER () AS total FROM licitacion l JOIN organo o ON o.id = l.organo_id" + consulta.where()
                + " ORDER BY " + orden(orden, filtro) + " LIMIT :limite OFFSET :desplazamiento";
        var parametros = new HashMap<>(consulta.parametros());
        parametros.put("ahora", OffsetDateTime.now(reloj));
        parametros.put("limite", tamano);
        parametros.put("desplazamiento", (pagina - 1) * tamano);

        var total = new long[] {-1};
        var elementos = jdbc.sql(sql)
                .params(parametros)
                .query((rs, fila) -> {
                    total[0] = rs.getLong("total");
                    return resumen(rs);
                })
                .list();
        if (total[0] < 0) {
            // Página más allá del final: no hay filas de las que sacar el total.
            total[0] = jdbc.sql("SELECT count(*) FROM licitacion l" + consulta.where())
                    .params(consulta.parametros())
                    .query(Long.class)
                    .single();
        }
        return new Pagina<>(elementos, pagina, tamano, total[0]);
    }

    @Override
    public List<Long> novedades(
            FiltroDeBusqueda filtro,
            Instant ingeridasDesde,
            Instant ingeridasHasta,
            LocalDate publicadasDesde,
            int limite) {
        // De una alerta se avisa de lo que se puede presentar: en plazo y no anulado, sea cual sea el estado que
        // tenga guardado el filtro.
        var soloEnPlazo = new FiltroDeBusqueda(
                filtro.texto(),
                List.of("PUB"),
                filtro.tiposContrato(),
                filtro.procedimientos(),
                filtro.nuts(),
                filtro.cpv(),
                filtro.importeMinimo(),
                filtro.importeMaximo(),
                null,
                null,
                publicadasDesde,
                null,
                filtro.organoId(),
                true,
                filtro.soloFondosUe(),
                false);
        var consulta = new Condiciones(soloEnPlazo, OffsetDateTime.now(reloj));
        var sql = "SELECT l.id FROM licitacion l" + consulta.where()
                + " AND l.ingerida_en BETWEEN :ingeridasDesde AND :ingeridasHasta"
                + " ORDER BY l.fecha_publicacion DESC, l.id DESC LIMIT :limite";
        var parametros = new HashMap<>(consulta.parametros());
        parametros.put("ingeridasDesde", OffsetDateTime.ofInstant(ingeridasDesde, ZONA));
        parametros.put("ingeridasHasta", OffsetDateTime.ofInstant(ingeridasHasta, ZONA));
        parametros.put("limite", limite);
        return jdbc.sql(sql).params(parametros).query(Long.class).list();
    }

    @Override
    public List<LicitacionResumen> porIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var encontradas = jdbc
                .sql("SELECT " + COLUMNAS
                        + " FROM licitacion l JOIN organo o ON o.id = l.organo_id WHERE l.id = ANY (:ids)")
                .param("ids", ids.toArray(Long[]::new))
                .query((rs, fila) -> resumen(rs))
                .list()
                .stream()
                .collect(Collectors.toMap(LicitacionResumen::id, Function.identity()));
        return ids.stream().map(encontradas::get).filter(Objects::nonNull).toList();
    }

    private static String orden(OrdenDeBusqueda orden, FiltroDeBusqueda filtro) {
        return switch (orden) {
            case RELEVANCIA ->
                filtro.texto() == null
                        ? "l.fecha_publicacion DESC NULLS LAST, l.id DESC"
                        : "ts_rank_cd(l.busqueda, websearch_to_tsquery('radar_es', :texto)) DESC, l.id DESC";
            case PUBLICACION -> "l.fecha_publicacion DESC NULLS LAST, l.id DESC";
            // Primero las que aún se pueden presentar, de la que antes cierra a la que más tarda.
            case PLAZO -> "l.plazo_presentacion < :ahora, l.plazo_presentacion ASC NULLS LAST, l.id DESC";
            case IMPORTE -> "l.importe_sin_iva DESC NULLS LAST, l.id DESC";
        };
    }

    static LicitacionResumen resumen(ResultSet rs) throws SQLException {
        return new LicitacionResumen(
                rs.getLong("id"),
                rs.getString("expediente"),
                rs.getString("titulo"),
                rs.getString("estado"),
                rs.getString("tipo_contrato"),
                rs.getString("procedimiento"),
                rs.getLong("organo_id"),
                rs.getString("organo"),
                rs.getBigDecimal("importe_sin_iva"),
                rs.getObject("plazo_presentacion", OffsetDateTime.class),
                rs.getObject("fecha_publicacion", LocalDate.class),
                rs.getString("nuts"),
                rs.getString("lugar"),
                textos(rs.getArray("cpv")),
                rs.getInt("num_lotes"),
                rs.getBoolean("financiacion_ue"),
                rs.getBoolean("anulada"));
    }

    static List<String> textos(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    /** Las condiciones {@code WHERE} y sus parámetros para un filtro. */
    static final class Condiciones {

        private final List<String> condiciones = new ArrayList<>();
        private final Map<String, Object> parametros = new HashMap<>();

        Condiciones(FiltroDeBusqueda f, OffsetDateTime ahora) {
            if (!f.incluirAnuladas()) {
                condiciones.add("NOT l.anulada");
            }
            if (f.texto() != null) {
                condiciones.add("l.busqueda @@ websearch_to_tsquery('radar_es', :texto)");
                parametros.put("texto", f.texto());
            }
            enLista("l.estado", "estados", f.estados());
            enLista("l.tipo_contrato", "tiposContrato", f.tiposContrato());
            enLista("l.procedimiento", "procedimientos", f.procedimientos());
            if (!f.nuts().isEmpty()) {
                f.nuts().forEach(codigo -> validar(Prefijos.esNutsValido(codigo), "Código NUTS no válido: " + codigo));
                condiciones.add("l.nuts_prefijos && CAST(:nuts AS text[])");
                parametros.put("nuts", f.nuts().toArray(String[]::new));
            }
            if (!f.cpv().isEmpty()) {
                f.cpv().forEach(codigo -> validar(Prefijos.esCpvValido(codigo), "Código CPV no válido: " + codigo));
                condiciones.add("l.cpv_prefijos && CAST(:cpv AS text[])");
                parametros.put(
                        "cpv",
                        f.cpv().stream().map(Prefijos::normalizarCpv).distinct().toArray(String[]::new));
            }
            if (f.importeMinimo() != null) {
                condiciones.add("l.importe_sin_iva >= :importeMinimo");
                parametros.put("importeMinimo", f.importeMinimo());
            }
            if (f.importeMaximo() != null) {
                condiciones.add("l.importe_sin_iva <= :importeMaximo");
                parametros.put("importeMaximo", f.importeMaximo());
            }
            if (f.plazoDesde() != null) {
                condiciones.add("l.plazo_presentacion >= :plazoDesde");
                parametros.put("plazoDesde", inicioDelDia(f.plazoDesde()));
            }
            if (f.plazoHasta() != null) {
                condiciones.add("l.plazo_presentacion < :plazoHasta");
                parametros.put("plazoHasta", inicioDelDia(f.plazoHasta().plusDays(1)));
            }
            if (f.publicadaDesde() != null) {
                condiciones.add("l.fecha_publicacion >= :publicadaDesde");
                parametros.put("publicadaDesde", f.publicadaDesde());
            }
            if (f.publicadaHasta() != null) {
                condiciones.add("l.fecha_publicacion <= :publicadaHasta");
                parametros.put("publicadaHasta", f.publicadaHasta());
            }
            if (f.organoId() != null) {
                condiciones.add("l.organo_id = :organoId");
                parametros.put("organoId", f.organoId());
            }
            if (f.soloPlazoAbierto()) {
                condiciones.add("(l.plazo_presentacion IS NULL OR l.plazo_presentacion > :ahora)");
                parametros.put("ahora", ahora);
            }
            if (f.soloFondosUe()) {
                condiciones.add("l.financiacion_ue");
            }
        }

        private void enLista(String columna, String nombre, List<String> valores) {
            if (!valores.isEmpty()) {
                condiciones.add(columna + " = ANY (CAST(:" + nombre + " AS text[]))");
                parametros.put(nombre, valores.toArray(String[]::new));
            }
        }

        private static void validar(boolean valido, String mensaje) {
            if (!valido) {
                throw new PeticionNoValida(mensaje);
            }
        }

        private static OffsetDateTime inicioDelDia(LocalDate dia) {
            return dia.atStartOfDay(ZONA).toOffsetDateTime();
        }

        String where() {
            return condiciones.isEmpty() ? " WHERE true" : " WHERE " + String.join(" AND ", condiciones);
        }

        Map<String, Object> parametros() {
            return parametros;
        }
    }
}
