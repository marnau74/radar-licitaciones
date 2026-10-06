package es.radarlicitaciones.licitaciones.internal;

import es.radarlicitaciones.catalogos.Catalogo;
import es.radarlicitaciones.catalogos.Catalogos;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** La ficha completa de una licitación: datos, órgano, lotes, resultados y documentos. */
@Repository
@Transactional(readOnly = true)
public class DetalleJdbc {

    private static final TypeReference<List<DetalleDeLicitacion.Documento>> DOCUMENTOS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final Catalogos catalogos;
    private final JsonMapper json;

    DetalleJdbc(JdbcClient jdbc, Catalogos catalogos, JsonMapper json) {
        this.jdbc = jdbc;
        this.catalogos = catalogos;
        this.json = json;
    }

    public Optional<DetalleDeLicitacion> buscar(long id) {
        var lotes = jdbc
                .sql("SELECT numero, objeto, importe_sin_iva, cpv, nuts FROM lote WHERE licitacion_id = ?")
                .param(id)
                .query((rs, fila) -> new DetalleDeLicitacion.Lote(
                        rs.getString("numero"),
                        rs.getString("objeto"),
                        rs.getBigDecimal("importe_sin_iva"),
                        catalogos.codigos(Catalogo.CPV, BuscadorJdbc.textos(rs.getArray("cpv"))),
                        catalogos.codigo(Catalogo.NUTS, rs.getString("nuts")).orElse(null)))
                .list()
                .stream()
                .sorted(java.util.Comparator.comparing(DetalleDeLicitacion.Lote::numero, DetalleJdbc::compararNumeros))
                .toList();
        var resultados = jdbc.sql("""
                        SELECT lote, codigo, fecha_adjudicacion, ofertas_recibidas, adjudicatario, adjudicatario_nif,
                               importe_sin_iva, importe_con_iva, pyme
                        FROM resultado WHERE licitacion_id = ? ORDER BY id
                        """)
                .param(id)
                .query((rs, fila) -> new DetalleDeLicitacion.Resultado(
                        rs.getString("lote"),
                        catalogos
                                .codigo(Catalogo.RESULTADO, rs.getString("codigo"))
                                .orElse(null),
                        rs.getObject("fecha_adjudicacion", LocalDate.class),
                        rs.getObject("ofertas_recibidas", Integer.class),
                        rs.getString("adjudicatario"),
                        rs.getString("adjudicatario_nif"),
                        rs.getBigDecimal("importe_sin_iva"),
                        rs.getBigDecimal("importe_con_iva"),
                        rs.getObject("pyme", Boolean.class)))
                .list();

        return jdbc.sql("""
                        SELECT l.*, o.id AS o_id, o.nombre AS o_nombre, o.nif AS o_nif, o.dir3 AS o_dir3,
                               o.tipo AS o_tipo, o.ciudad AS o_ciudad, o.codigo_postal AS o_codigo_postal,
                               o.web AS o_web, o.perfil_contratante AS o_perfil, o.jerarquia AS o_jerarquia
                        FROM licitacion l JOIN organo o ON o.id = l.organo_id
                        WHERE l.id = ?
                        """)
                .param(id)
                .query((rs, fila) -> new DetalleDeLicitacion(
                        rs.getLong("id"),
                        rs.getString("expediente"),
                        rs.getString("titulo"),
                        rs.getString("url"),
                        catalogos
                                .codigo(Catalogo.ESTADO, rs.getString("estado"))
                                .orElse(null),
                        catalogos
                                .codigo(Catalogo.TIPO_CONTRATO, rs.getString("tipo_contrato"))
                                .orElse(null),
                        rs.getString("subtipo_contrato"),
                        catalogos
                                .codigo(Catalogo.PROCEDIMIENTO, rs.getString("procedimiento"))
                                .orElse(null),
                        new DetalleDeLicitacion.Organo(
                                rs.getLong("o_id"),
                                rs.getString("o_nombre"),
                                rs.getString("o_nif"),
                                rs.getString("o_dir3"),
                                catalogos
                                        .codigo(Catalogo.TIPO_ORGANO, rs.getString("o_tipo"))
                                        .orElse(null),
                                rs.getString("o_ciudad"),
                                rs.getString("o_codigo_postal"),
                                rs.getString("o_web"),
                                rs.getString("o_perfil"),
                                BuscadorJdbc.textos(rs.getArray("o_jerarquia"))),
                        rs.getBigDecimal("importe_sin_iva"),
                        rs.getBigDecimal("importe_con_iva"),
                        rs.getBigDecimal("valor_estimado"),
                        catalogos.codigos(Catalogo.CPV, BuscadorJdbc.textos(rs.getArray("cpv"))),
                        catalogos.codigo(Catalogo.NUTS, rs.getString("nuts")).orElse(null),
                        rs.getString("lugar"),
                        rs.getObject("plazo_presentacion", OffsetDateTime.class),
                        rs.getObject("fecha_publicacion", LocalDate.class),
                        rs.getString("duracion"),
                        rs.getBoolean("financiacion_ue"),
                        rs.getBoolean("anulada"),
                        lotes,
                        resultados,
                        json.readValue(rs.getString("documentos"), DOCUMENTOS),
                        rs.getObject("actualizada_en", OffsetDateTime.class),
                        rs.getObject("vista_por_primera_vez", OffsetDateTime.class)))
                .optional();
    }

    /** Los lotes se numeran 1, 2... 10: los numéricos van primero y por valor; el resto, después y por texto. */
    private static int compararNumeros(String a, String b) {
        var numeroA = numero(a);
        var numeroB = numero(b);
        if (numeroA.isPresent() && numeroB.isPresent()) {
            return Long.compare(numeroA.get(), numeroB.get());
        }
        if (numeroA.isPresent() != numeroB.isPresent()) {
            return numeroA.isPresent() ? -1 : 1;
        }
        return a.compareTo(b);
    }

    private static Optional<Long> numero(String texto) {
        return texto.matches("\\d{1,18}") ? Optional.of(Long.parseLong(texto)) : Optional.empty();
    }
}
