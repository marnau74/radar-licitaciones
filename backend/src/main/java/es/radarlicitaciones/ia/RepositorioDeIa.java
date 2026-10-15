package es.radarlicitaciones.ia;

import es.radarlicitaciones.licitaciones.TextoParaIndexar;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Las tablas del módulo, con SQL. Los vectores viajan como texto ({@code [0.1,0.2,...]}) y se convierten en SQL. */
@Repository
class RepositorioDeIa {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    RepositorioDeIa(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // --- Resúmenes -------------------------------------------------------------------------------------------------

    private static final String COLUMNAS_RESUMEN =
            "licitacion_id, documento_url, documento_nombre, modelo, resumen, aviso, creado_en";

    Optional<ResumenGuardado> resumen(long licitacionId, String url) {
        return jdbc.sql("SELECT " + COLUMNAS_RESUMEN
                        + " FROM resumen_pliego WHERE licitacion_id = :id AND documento_url = :url")
                .param("id", licitacionId)
                .param("url", url)
                .query((rs, fila) -> resumen(rs))
                .optional();
    }

    List<ResumenGuardado> resumenes(long licitacionId) {
        return jdbc.sql("SELECT " + COLUMNAS_RESUMEN
                        + " FROM resumen_pliego WHERE licitacion_id = :id ORDER BY creado_en")
                .param("id", licitacionId)
                .query((rs, fila) -> resumen(rs))
                .list();
    }

    /** Si dos personas lo piden a la vez, se queda el primero que termine (los dos son válidos). */
    void guardar(ResumenGuardado r, String pedidoPor) {
        jdbc.sql("""
                        INSERT INTO resumen_pliego (licitacion_id, documento_url, documento_nombre, modelo, resumen, aviso,
                                                    pedido_por, creado_en)
                        VALUES (:id, :url, :nombre, :modelo, CAST(:resumen AS jsonb), :aviso, :pedidoPor, :creadoEn)
                        ON CONFLICT (licitacion_id, documento_url) DO NOTHING
                        """)
                .param("id", r.licitacionId())
                .param("url", r.documentoUrl())
                .param("nombre", r.documentoNombre())
                .param("modelo", r.modelo())
                .param("resumen", json.writeValueAsString(r.resumen()))
                .param("aviso", r.aviso())
                .param("pedidoPor", pedidoPor)
                .param("creadoEn", r.creadoEn())
                .update();
    }

    /**
     * Apunta un resumen más al usuario en ese día si no ha llegado al límite. Lo decide PostgreSQL en una sola
     * sentencia: dos peticiones a la vez no se saltan el límite.
     */
    boolean consumirUso(String usuario, LocalDate dia, int limite) {
        if (limite <= 0) {
            return false;
        }
        return jdbc.sql("""
                        INSERT INTO uso_ia (usuario, dia, resumenes) VALUES (:usuario, :dia, 1)
                        ON CONFLICT (usuario, dia) DO UPDATE SET resumenes = uso_ia.resumenes + 1
                         WHERE uso_ia.resumenes < :limite
                        """)
                        .param("usuario", usuario)
                        .param("dia", dia)
                        .param("limite", limite)
                        .update()
                == 1;
    }

    private ResumenGuardado resumen(ResultSet rs) throws SQLException {
        return new ResumenGuardado(
                rs.getLong("licitacion_id"),
                rs.getString("documento_url"),
                rs.getString("documento_nombre"),
                rs.getString("modelo"),
                json.readValue(rs.getString("resumen"), ResumenDePliego.class),
                rs.getString("aviso"),
                rs.getObject("creado_en", OffsetDateTime.class));
    }

    // --- Vectores --------------------------------------------------------------------------------------------------

    record Posicion(Instant ingeridaEn, long licitacionId) {
        static final Posicion INICIO = new Posicion(Instant.EPOCH, 0);
    }

    Posicion cursor(String nombre) {
        return jdbc.sql("SELECT ingerida_en, licitacion_id FROM ia_cursor WHERE nombre = :nombre")
                .param("nombre", nombre)
                .query((rs, fila) -> new Posicion(
                        rs.getObject("ingerida_en", OffsetDateTime.class).toInstant(), rs.getLong("licitacion_id")))
                .optional()
                .orElse(Posicion.INICIO);
    }

    void guardarCursor(String nombre, Posicion posicion) {
        jdbc.sql("""
                        INSERT INTO ia_cursor (nombre, ingerida_en, licitacion_id) VALUES (:nombre, :ingerida, :id)
                        ON CONFLICT (nombre) DO UPDATE SET ingerida_en = EXCLUDED.ingerida_en,
                                                           licitacion_id = EXCLUDED.licitacion_id
                        """)
                .param("nombre", nombre)
                .param("ingerida", OffsetDateTime.ofInstant(posicion.ingeridaEn(), ZoneOffset.UTC))
                .param("id", posicion.licitacionId())
                .update();
    }

    void guardarVector(TextoParaIndexar texto, float[] vector, String modelo, OffsetDateTime ahora) {
        jdbc.sql("""
                        INSERT INTO licitacion_vector (licitacion_id, modelo, vector, plazo_presentacion, anulada, indexada_en)
                        VALUES (:id, :modelo, CAST(:vector AS vector), :plazo, :anulada, :ahora)
                        ON CONFLICT (licitacion_id) DO UPDATE SET modelo = EXCLUDED.modelo, vector = EXCLUDED.vector,
                               plazo_presentacion = EXCLUDED.plazo_presentacion, anulada = EXCLUDED.anulada,
                               indexada_en = EXCLUDED.indexada_en
                        """)
                .param("id", texto.id())
                .param("modelo", modelo)
                .param("vector", comoTexto(vector))
                .param("plazo", texto.plazoPresentacion())
                .param("anulada", texto.anulada())
                .param("ahora", ahora)
                .update();
    }

    record Cercana(long licitacionId, double similitud) {}

    /** Las licitaciones más cercanas al vector (similitud coseno de 1 a -1), sin anuladas. */
    List<Cercana> cercanas(float[] vector, boolean soloAbiertas, OffsetDateTime ahora, int limite) {
        return jdbc.sql("""
                        SELECT licitacion_id, 1 - (vector <=> CAST(:vector AS vector)) AS similitud
                          FROM licitacion_vector
                         WHERE NOT anulada AND (NOT :soloAbiertas OR plazo_presentacion > :ahora)
                         ORDER BY vector <=> CAST(:vector AS vector)
                         LIMIT :limite
                        """)
                .param("vector", comoTexto(vector))
                .param("soloAbiertas", soloAbiertas)
                .param("ahora", ahora)
                .param("limite", limite)
                .query((rs, fila) -> new Cercana(rs.getLong("licitacion_id"), rs.getDouble("similitud")))
                .list();
    }

    long vectoresGuardados() {
        return jdbc.sql("SELECT count(*) FROM licitacion_vector")
                .query(Long.class)
                .single();
    }

    static String comoTexto(float[] vector) {
        var texto = new StringBuilder(vector.length * 10).append('[');
        for (var i = 0; i < vector.length; i++) {
            if (i > 0) {
                texto.append(',');
            }
            texto.append(vector[i]);
        }
        return texto.append(']').toString();
    }
}
