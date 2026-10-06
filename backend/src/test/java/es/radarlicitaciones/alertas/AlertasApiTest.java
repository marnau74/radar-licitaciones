package es.radarlicitaciones.alertas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import es.radarlicitaciones.PruebaDeIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AlertasApiTest extends PruebaDeIntegracion {

    private static final String ALERTA = """
            {"nombre": "Obras en Galicia", "tiposContrato": ["3"], "nuts": ["ES11"], "importeMinimo": 10000}
            """;

    private static RequestPostProcessor usuario(String id) {
        return jwt().jwt(token ->
                token.subject(id).claim("email", id + "@demo.example").claim("name", id));
    }

    private long crear(String usuario, String cuerpo) throws Exception {
        var respuesta = mvc.post()
                .uri("/api/v1/alertas")
                .with(usuario(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)
                .exchange();
        assertThat(respuesta).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(respuesta.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void creaListaModificaYBorraLasAlertasPropias() throws Exception {
        var id = crear("ana", ALERTA);

        assertThat(mvc.get().uri("/api/v1/alertas").with(usuario("ana")).exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].correo")
                .isEqualTo("ana@demo.example");

        var modificada = mvc.put()
                .uri("/api/v1/alertas/" + id)
                .with(usuario("ana"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre": "Obras en Galicia y Asturias", "tiposContrato": ["3"], "nuts": ["ES11", "ES12"],
                         "porCorreo": false}
                        """)
                .exchange();
        assertThat(modificada).hasStatusOk();
        assertThat(modificada).bodyJson().extractingPath("$.nuts").asArray().containsExactly("ES11", "ES12");
        assertThat(modificada).bodyJson().extractingPath("$.porCorreo").isEqualTo(false);

        assertThat(mvc.delete()
                        .uri("/api/v1/alertas/" + id)
                        .with(usuario("ana"))
                        .exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/v1/alertas/" + id).with(usuario("ana")).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void nadieVeNiTocaLasAlertasDeOtro() throws Exception {
        var id = crear("ana", ALERTA);

        assertThat(mvc.get().uri("/api/v1/alertas/" + id).with(usuario("luis")).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete()
                        .uri("/api/v1/alertas/" + id)
                        .with(usuario("luis"))
                        .exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/alertas").with(usuario("luis")).exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty();
    }

    @Test
    void unaAlertaNecesitaAlgunCriterioYDatosValidos() {
        var sinCriterio = mvc.post()
                .uri("/api/v1/alertas")
                .with(usuario("ana"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\": \"Todo\"}")
                .exchange();
        assertThat(sinCriterio).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinCriterio).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("conAlgunCriterio");

        var malas = mvc.post()
                .uri("/api/v1/alertas")
                .with(usuario("ana"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre": "", "cpv": ["abc"], "importeMinimo": 500, "importeMaximo": 100}
                        """)
                .exchange();
        assertThat(malas).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(malas)
                .bodyJson()
                .extractingPath("$.errores[*].campo")
                .asArray()
                .contains("nombre", "importesCoherentes");
    }

    @Test
    void hayUnLimiteDeAlertasPorUsuario() throws Exception {
        for (int i = 0; i < AlertasController.MAX_ALERTAS; i++) {
            crear("ana", ALERTA);
        }
        var otra = mvc.post()
                .uri("/api/v1/alertas")
                .with(usuario("ana"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ALERTA)
                .exchange();
        assertThat(otra).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void losAvisosSonDeCadaUno() {
        assertThat(mvc.get().uri("/api/v1/avisos").with(usuario("ana")).exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.total")
                .isEqualTo(0);
        assertThat(mvc.get()
                        .uri("/api/v1/avisos/no-leidos")
                        .with(usuario("ana"))
                        .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.total")
                .isEqualTo(0);
        assertThat(mvc.post().uri("/api/v1/avisos/leidos").with(usuario("ana")).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
    }
}
