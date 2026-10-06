package es.radarlicitaciones.ingesta;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Hasta dónde se ha leído el feed vivo: la entrada más reciente de la última ingesta del feed completada. */
@Repository
class MarcaDelFeed {

    private static final String FEED = "feed";

    private final JdbcClient jdbc;

    MarcaDelFeed(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<OffsetDateTime> leer() {
        return jdbc.sql("SELECT actualizado_en FROM ingesta_marca WHERE origen = ?")
                .param(FEED)
                .query(OffsetDateTime.class)
                .optional();
    }

    /** Solo avanza: una ingesta que ha leído menos no hace retroceder la marca. */
    void avanzar(OffsetDateTime hasta) {
        jdbc.sql("""
                        INSERT INTO ingesta_marca (origen, actualizado_en) VALUES (?, ?)
                        ON CONFLICT (origen) DO UPDATE
                            SET actualizado_en = GREATEST(ingesta_marca.actualizado_en, EXCLUDED.actualizado_en)
                        """).param(FEED).param(hasta).update();
    }
}
