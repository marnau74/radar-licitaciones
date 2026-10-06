package es.radarlicitaciones;

import org.springframework.boot.SpringApplication;

/**
 * Arranca la API en local con PostgreSQL, Keycloak y Mailpit en contenedores, sin compose:
 * {@code ./mvnw spring-boot:test-run}.
 */
public class TestRadarApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(RadarApiApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
