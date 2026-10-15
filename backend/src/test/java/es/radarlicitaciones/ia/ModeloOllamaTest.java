package es.radarlicitaciones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.ErrorResponseException;
import tools.jackson.databind.json.JsonMapper;

/** Con Ollama: el texto del PDF, el esquema JSON y el aviso cuando el pliego no cabe entero. */
class ModeloOllamaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ServidorFalso ollama = new ServidorFalso();

    @AfterEach
    void cerrar() {
        ollama.close();
    }

    private ModeloOllama modelo(int maxPaginas) {
        var config = new PropiedadesDeIa.Ollama(
                URI.create(ollama.url()), "qwen3:8b", "bge-m3", 32768, Duration.ofSeconds(10));
        return new ModeloOllama(config, maxPaginas, JSON);
    }

    /** Un PDF de verdad con una línea de texto por página. */
    static byte[] pdf(String... paginas) throws IOException {
        try (var documento = new PDDocument()) {
            for (var texto : paginas) {
                var pagina = new PDPage();
                documento.addPage(pagina);
                try (var contenido = new PDPageContentStream(documento, pagina)) {
                    contenido.beginText();
                    contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contenido.newLineAtOffset(72, 700);
                    contenido.showText(texto);
                    contenido.endText();
                }
            }
            var salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private void responderResumen() {
        var resumen = """
                {"objeto": "Limpieza de colegios", "presupuesto": "No consta en este documento", "duracion": "2 años",
                 "solvenciaEconomica": [], "solvenciaTecnica": ["Certificado ISO 14001"], "criteriosDeAdjudicacion": [],
                 "garantias": [], "puntosDeAtencion": ["Subrogación de 12 trabajadores"]}
                """;
        ollama.responder(
                200, JSON.writeValueAsString(java.util.Map.of("message", java.util.Map.of("content", resumen))));
    }

    @Test
    void mandaElTextoDelPdfConElEsquemaYElContextoAmplio() throws IOException {
        responderResumen();

        var resultado =
                modelo(10).resumir(pdf("Pliego de limpieza de centros educativos"), "«Limpieza» (Ayuntamiento)");

        assertThat(resultado.resumen().puntosDeAtencion()).containsExactly("Subrogación de 12 trabajadores");
        assertThat(resultado.modelo()).isEqualTo("ollama/qwen3:8b");
        assertThat(resultado.aviso()).isNull();

        var peticion = ollama.peticiones().getFirst();
        assertThat(peticion.ruta()).isEqualTo("/api/chat");
        var cuerpo = JSON.readTree(peticion.cuerpo());
        assertThat(cuerpo.path("model").asString()).isEqualTo("qwen3:8b");
        assertThat(cuerpo.path("stream").asBoolean()).isFalse();
        assertThat(cuerpo.path("options").path("num_ctx").asInt()).isEqualTo(32768);
        assertThat(cuerpo.path("format").path("required").toString()).contains("criteriosDeAdjudicacion");
        assertThat(cuerpo.path("messages").get(1).path("content").asString())
                .contains("Pliego de limpieza de centros educativos");
    }

    @Test
    void siElPliegoTieneMasPaginasDeLasQueSeLeenLoAvisa() throws IOException {
        responderResumen();

        var resultado = modelo(2).resumir(pdf("Página uno", "Página dos", "Página tres"), "x");

        assertThat(resultado.aviso()).contains("primeras 2 páginas");
        assertThat(ollama.peticiones().getFirst().cuerpo())
                .contains("Página dos")
                .doesNotContain("Página tres");
    }

    @Test
    void unPdfSinTextoNoSeMandaAlModelo() throws IOException {
        assertThatThrownBy(() -> modelo(10).resumir(pdf(""), "x")).isInstanceOf(ErrorResponseException.class);
        assertThat(ollama.peticiones()).isEmpty();
    }
}
