package es.radarlicitaciones.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import es.radarlicitaciones.PruebaDeIntegracion;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SeguridadTest extends PruebaDeIntegracion {

    @Autowired
    KeycloakContainer keycloak;

    @Test
    void loPublicoNoPideSesion() {
        assertThat(mvc.get().uri("/api/v1/licitaciones").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/catalogos").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/ingesta/estado").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/openapi").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/swagger-ui/index.html").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatusOk();
    }

    @Test
    void loPrivadoPideSesionYLoDeAdministracionElRol() {
        assertThat(mvc.get().uri("/api/v1/alertas").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/yo").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/v1/admin/ingestas").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.get().uri("/api/v1/admin/ingestas").with(jwt()).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/actuator/metrics").with(jwt()).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get()
                        .uri("/api/v1/admin/ingestas")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))
                        .exchange())
                .hasStatusOk();
    }

    @Test
    void loQueNoEstaDeclaradoSeDeniega() {
        assertThat(mvc.delete().uri("/api/v1/licitaciones/1").with(jwt()).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/inventado").exchange()).hasStatus4xxClientError();
    }

    @Test
    void unTokenFalsoNoSirve() {
        assertThat(mvc.get()
                        .uri("/api/v1/yo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.firma")
                        .exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Con el realm de desarrollo en un Keycloak real: el token de un usuario lleva la audiencia de la API y los roles
     * donde la API los espera. El cliente web no admite usuario y contraseña directamente (solo el flujo con PKCE); la
     * prueba lo activa por la API de administración para poder pedir el token sin navegador.
     */
    @Test
    void validaLosTokensDeKeycloakConSuAudienciaYRoles() throws Exception {
        var realm = keycloak.getKeycloakAdminClient().realm("radar");
        var cliente = realm.clients().findByClientId("radar-web").getFirst();
        cliente.setDirectAccessGrantsEnabled(true);
        realm.clients().get(cliente.getId()).update(cliente);

        var deAna = token("ana@demo.example");
        assertThat(mvc.get()
                        .uri("/api/v1/yo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deAna)
                        .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.correo")
                .isEqualTo("ana@demo.example");
        assertThat(mvc.get()
                        .uri("/api/v1/admin/ingestas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deAna)
                        .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);

        var deAdmin = token("admin@demo.example");
        assertThat(mvc.get()
                        .uri("/api/v1/admin/ingestas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deAdmin)
                        .exchange())
                .hasStatusOk();
        assertThat(mvc.post()
                        .uri("/api/v1/admin/ingestas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\": \"otro\"}")
                        .exchange())
                .as("autorizado, pero la petición no es válida")
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private String token(String usuario) throws Exception {
        var formulario = "grant_type=password&client_id=radar-web&scope=openid&username="
                + URLEncoder.encode(usuario, StandardCharsets.UTF_8) + "&password=radar-demo-2026";
        var respuesta = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(
                                        keycloak.getAuthServerUrl() + "/realms/radar/protocol/openid-connect/token"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(formulario))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return JsonPath.read(respuesta.body(), "$.access_token");
    }
}
