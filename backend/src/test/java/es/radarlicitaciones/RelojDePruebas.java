package es.radarlicitaciones;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Fija el reloj de la aplicación en el día en que se recortaron los datos de prueba. */
@TestConfiguration(proxyBeanMethods = false)
public class RelojDePruebas {

    public static final ZonedDateTime AHORA = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, ZoneId.of("Europe/Madrid"));

    @Bean
    @Primary
    Clock relojFijo() {
        return Clock.fixed(AHORA.toInstant(), AHORA.getZone());
    }
}
