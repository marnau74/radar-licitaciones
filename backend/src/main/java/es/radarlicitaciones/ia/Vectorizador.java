package es.radarlicitaciones.ia;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/** Convierte textos en vectores para la búsqueda por significado. */
interface Vectorizador {

    List<float[]> vectorizar(List<String> textos);

    String modelo();

    /**
     * Embeddings locales con Ollama ({@code POST /api/embed}). Por defecto {@code bge-m3}: multilingüe (va bien en
     * español), 1024 dimensiones y gratis. Anthropic no ofrece un modelo de embeddings, así que con Claude para los
     * resúmenes la búsqueda sigue usando esto.
     */
    final class Ollama implements Vectorizador {

        private final RestClient http;
        private final JsonMapper json;
        private final String modelo;
        private final int dimensiones;

        Ollama(PropiedadesDeIa propiedades, JsonMapper json) {
            this.json = json;
            this.modelo = propiedades.ollama().modeloEmbeddings();
            this.dimensiones = propiedades.embeddings().dimensiones();
            var fabrica = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
            fabrica.setReadTimeout(Duration.ofMinutes(2));
            this.http = RestClient.builder()
                    .baseUrl(propiedades.ollama().url().toString())
                    .requestFactory(fabrica)
                    .build();
        }

        @Override
        public String modelo() {
            return "ollama/" + modelo;
        }

        @Override
        public List<float[]> vectorizar(List<String> textos) {
            try {
                var respuesta = http.post()
                        .uri("/api/embed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json.writeValueAsString(Map.of("model", modelo, "input", textos)))
                        .retrieve()
                        .body(String.class);
                var vectores = new ArrayList<float[]>();
                for (var fila : json.readTree(respuesta).path("embeddings")) {
                    var vector = new float[fila.size()];
                    for (var i = 0; i < vector.length; i++) {
                        vector[i] = (float) fila.get(i).asDouble();
                    }
                    if (vector.length != dimensiones) {
                        throw new IllegalStateException("El modelo " + modelo + " da vectores de " + vector.length
                                + " dimensiones y la base de datos espera " + dimensiones);
                    }
                    vectores.add(vector);
                }
                if (vectores.size() != textos.size()) {
                    throw ErrorDeIa.servicioExterno(
                            "Ollama devolvió " + vectores.size() + " vectores para " + textos.size() + " textos", null);
                }
                return vectores;
            } catch (RestClientException e) {
                throw ErrorDeIa.servicioExterno("No se pudo calcular los vectores con Ollama", e);
            }
        }
    }
}
