package es.radarlicitaciones.compartido;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La hora se inyecta para que los tests puedan fijarla. */
@Configuration(proxyBeanMethods = false)
class RelojConfig {

    @Bean
    Clock reloj() {
        return Clock.systemDefaultZone();
    }
}
