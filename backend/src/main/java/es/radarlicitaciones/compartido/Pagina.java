package es.radarlicitaciones.compartido;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.function.Function;

/**
 * Una página de resultados.
 *
 * @param pagina número de página, empezando en 1
 * @param total número total de resultados, en todas las páginas
 */
public record Pagina<T>(List<T> elementos, int pagina, int tamano, long total) {

    public Pagina {
        elementos = List.copyOf(elementos);
    }

    @JsonProperty("paginas")
    public int paginas() {
        return total == 0 ? 0 : (int) ((total + tamano - 1) / tamano);
    }

    public <R> Pagina<R> map(Function<T, R> conversion) {
        return new Pagina<>(elementos.stream().map(conversion).toList(), pagina, tamano, total);
    }
}
