package es.radarlicitaciones;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base de las pruebas de integración: la aplicación entera contra PostgreSQL, Keycloak y Mailpit reales. Todas
 * comparten configuración, así que Spring arranca el contexto (y los contenedores) una sola vez.
 *
 * <p>El reloj de la aplicación está fijo en el día en que se recortaron los datos de prueba (6 de octubre de 2026):
 * los plazos y las fechas de publicación de la muestra se interpretan siempre igual, se ejecuten cuando se ejecuten.
 */
@SpringBootTest(properties = {"radar.ingesta.programada=false", "radar.correo.intervalo=300ms"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojDePruebas.class})
public abstract class PruebaDeIntegracion {

    public static final ZonedDateTime AHORA = RelojDePruebas.AHORA;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MockMvcTester mvc;

    @BeforeEach
    void vaciarDatos() {
        jdbc.sql("""
                        TRUNCATE aviso, alerta, correo_saliente, resultado, lote, licitacion, organo, anulacion,
                                 ingesta, ingesta_marca, event_publication RESTART IDENTITY CASCADE
                        """).update();
    }
}
