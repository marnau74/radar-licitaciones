package es.radarlicitaciones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** La petición que se manda a la API de Anthropic y cómo se interpreta la respuesta, contra un servidor simulado. */
class ModeloClaudeTest {

    private static final byte[] PDF = "%PDF-1.4 pliego de prueba".getBytes(StandardCharsets.US_ASCII);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ServidorFalso anthropic = new ServidorFalso();

    @AfterEach
    void cerrar() {
        anthropic.close();
    }

    private ModeloClaude modelo() {
        return new ModeloClaude(new PropiedadesDeIa.Claude(
                "clave-de-prueba", "claude-opus-5-5", "medium", 16000, Duration.ofSeconds(10), anthropic.url()));
    }

    private static String respuesta(String motivo, String texto) {
        return JSON.writeValueAsString(java.util.Map.of(
                "id", "msg_prueba",
                "type", "message",
                "role", "assistant",
                "model", "claude-opus-5-5",
                "content", java.util.List.of(java.util.Map.of("type", "text", "text", texto)),
                "stop_reason", motivo,
                "usage", java.util.Map.of("input_tokens", 1200, "output_tokens", 300)));
    }

    private static final String RESUMEN = """
            {"objeto": "Seguro de responsabilidad civil del ayuntamiento", "presupuesto": "40.000 € sin IVA",
             "duracion": "1 año, prorrogable 1 más", "solvenciaEconomica": ["Volumen de negocio de 60.000 €"],
             "solvenciaTecnica": [], "criteriosDeAdjudicacion": [{"criterio": "Precio", "peso": "80 puntos",
             "automatico": true}], "garantias": ["Definitiva: 5 % del precio"], "puntosDeAtencion": ["Franquicia máxima de 600 €"]}
            """;

    @Test
    void mandaElPdfEnteroConSalidaEstructuradaYReservaDelServidor() {
        anthropic.responder(200, respuesta("end_turn", RESUMEN));

        var resultado = modelo().resumir(PDF, "«Seguros» (Ayuntamiento de Daganzo)");

        assertThat(resultado.resumen().objeto()).isEqualTo("Seguro de responsabilidad civil del ayuntamiento");
        assertThat(resultado.resumen().criteriosDeAdjudicacion())
                .singleElement()
                .satisfies(c -> assertThat(c.automatico()).isTrue());
        assertThat(resultado.modelo()).isEqualTo("claude/claude-opus-5-5");
        assertThat(resultado.aviso()).isNull();

        var peticion = anthropic.peticiones().getFirst();
        assertThat(peticion.ruta()).isEqualTo("/v1/messages");
        assertThat(peticion.cabeceras().getFirst("x-api-key")).isEqualTo("clave-de-prueba");
        assertThat(peticion.cabeceras().get("anthropic-beta")).anyMatch(v -> v.contains(ModeloClaude.BETA_RESERVA));

        JsonNode cuerpo = JSON.readTree(peticion.cuerpo());
        assertThat(cuerpo.path("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(cuerpo.path("fallbacks").asString()).isEqualTo("default");
        assertThat(cuerpo.path("output_config").path("effort").asString()).isEqualTo("medium");
        assertThat(cuerpo.path("output_config").path("format").path("type").asString())
                .isEqualTo("json_schema");
        assertThat(cuerpo.path("output_config")
                        .path("format")
                        .path("schema")
                        .path("properties")
                        .has("puntosDeAtencion"))
                .isTrue();
        assertThat(cuerpo.path("system").asString()).contains("No consta en este documento");
        var contenido = cuerpo.path("messages").get(0).path("content");
        assertThat(contenido.get(0).path("type").asString()).isEqualTo("document");
        assertThat(Base64.getDecoder()
                        .decode(contenido.get(0).path("source").path("data").asString()))
                .isEqualTo(PDF);
        assertThat(contenido.get(1).path("text").asString()).contains("Daganzo");
    }

    @Test
    void unRechazoDelModeloSeExplicaYNoSeGuardaComoResumen() {
        anthropic.responder(200, respuesta("refusal", ""));

        assertThatThrownBy(() -> modelo().resumir(PDF, "x"))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> assertThat(((ErrorResponseException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }

    @Test
    void unErrorDeLaApiEsUnFalloDelServicioExterno() {
        anthropic.responder(400, """
                {"type": "error", "error": {"type": "invalid_request_error", "message": "PDF demasiado grande"}}
                """);

        assertThatThrownBy(() -> modelo().resumir(PDF, "x"))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e ->
                        assertThat(((ErrorResponseException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
