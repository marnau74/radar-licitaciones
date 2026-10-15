package es.radarlicitaciones.ia;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resumen con un modelo local en Ollama: gratis y sin claves, para quien clone el repositorio. Los modelos locales no
 * leen PDF, así que se les da el texto (PDFBox), hasta un número de páginas; si el pliego es más largo, el resumen
 * lleva un aviso. La salida se restringe al mismo esquema JSON que con Claude.
 */
class ModeloOllama implements ModeloDeLenguaje {

    /** Unos 4 caracteres por token: se deja sitio para las instrucciones y la respuesta. */
    private static final double CARACTERES_POR_TOKEN = 3.5;

    private final PropiedadesDeIa.Ollama config;
    private final int maxPaginas;
    private final RestClient http;
    private final JsonMapper json;
    private final JsonNode esquema;

    ModeloOllama(PropiedadesDeIa.Ollama config, int maxPaginas, JsonMapper json) {
        this.config = config;
        this.maxPaginas = maxPaginas;
        this.json = json;
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        fabrica.setReadTimeout(config.tiempoMaximo());
        this.http = RestClient.builder()
                .baseUrl(config.url().toString())
                .requestFactory(fabrica)
                .build();
        try (var entrada = new ClassPathResource("ia/esquema-resumen.json").getInputStream()) {
            this.esquema = json.readTree(entrada);
        } catch (IOException e) {
            throw new IllegalStateException("No se encuentra ia/esquema-resumen.json", e);
        }
    }

    @Override
    public String descripcion() {
        return "ollama/" + config.modeloTexto();
    }

    @Override
    public Resultado resumir(byte[] pdf, String contexto) {
        var texto = texto(pdf);
        var maxCaracteres = (int) ((config.contexto() - 6000) * CARACTERES_POR_TOKEN);
        String aviso = texto.cortado()
                ? "El modelo local solo ha leído las primeras " + maxPaginas + " páginas del pliego."
                : null;
        var contenido = texto.texto();
        if (contenido.length() > maxCaracteres) {
            contenido = contenido.substring(0, maxCaracteres);
            aviso = "El pliego es más largo de lo que cabe en el modelo local: solo se ha leído el principio.";
        }
        if (contenido.isBlank()) {
            throw ErrorDeIa.pliegoNoValido(
                    "El PDF no tiene texto (probablemente es un escaneo): el modelo local no puede leerlo");
        }

        var cuerpo = Map.of(
                "model", config.modeloTexto(),
                "stream", false,
                "format", esquema,
                "options", Map.of("num_ctx", config.contexto(), "temperature", 0),
                "messages",
                        List.of(
                                Map.of("role", "system", "content", ModeloDeLenguaje.instrucciones()),
                                Map.of(
                                        "role",
                                        "user",
                                        "content",
                                        "Licitación: " + contexto + "\n\nTexto del pliego:\n\n" + contenido)));
        try {
            var respuesta = http.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(cuerpo))
                    .retrieve()
                    .body(String.class);
            var mensaje =
                    json.readTree(respuesta).path("message").path("content").asString();
            return new Resultado(json.readValue(mensaje, ResumenDePliego.class), descripcion(), aviso);
        } catch (RestClientException e) {
            throw ErrorDeIa.servicioExterno("No se pudo resumir con Ollama en " + config.url(), e);
        } catch (tools.jackson.core.JacksonException e) {
            throw ErrorDeIa.servicioExterno("El modelo local no devolvió un resumen válido", e);
        }
    }

    record TextoDelPdf(String texto, boolean cortado) {}

    TextoDelPdf texto(byte[] pdf) {
        try (var documento = Loader.loadPDF(pdf)) {
            var lector = new PDFTextStripper();
            var paginas = documento.getNumberOfPages();
            lector.setEndPage(Math.min(paginas, maxPaginas));
            return new TextoDelPdf(lector.getText(documento), paginas > maxPaginas);
        } catch (IOException e) {
            throw ErrorDeIa.pliegoNoValido("El PDF está dañado o protegido y no se puede leer");
        }
    }
}
