package es.radarlicitaciones;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Los mismos servicios que el compose de desarrollo, en contenedores efímeros y con las mismas versiones. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final int SMTP = 1025;
    static final int API_MAILPIT = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    KeycloakContainer keycloak() {
        return new KeycloakContainer("quay.io/keycloak/keycloak:26.8").withRealmImportFile("keycloak/realm-radar.json");
    }

    @Bean
    GenericContainer<?> mailpit() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31"))
                .withExposedPorts(SMTP, API_MAILPIT)
                .waitingFor(Wait.forHttp("/livez").forPort(API_MAILPIT));
    }

    @Bean
    DynamicPropertyRegistrar servicios(KeycloakContainer keycloak, GenericContainer<?> mailpit) {
        return propiedades -> {
            propiedades.add(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                    () -> keycloak.getAuthServerUrl() + "/realms/radar");
            propiedades.add("spring.mail.host", mailpit::getHost);
            propiedades.add("spring.mail.port", () -> mailpit.getMappedPort(SMTP));
            propiedades.add(
                    "pruebas.mailpit-url",
                    () -> "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(API_MAILPIT));
        };
    }
}
