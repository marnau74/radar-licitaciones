package es.radarlicitaciones.licitaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Criterios de búsqueda. Dentro de cada lista basta con que se cumpla uno; entre criterios distintos, todos.
 *
 * @param texto palabras a buscar en el título, el órgano, los lotes y los CPV (admite "frases" y -exclusiones)
 * @param nuts códigos NUTS o prefijos: ES51 es Cataluña entera, ES511 solo Barcelona
 * @param cpv códigos CPV o prefijos: 72 son todos los servicios de TI
 * @param plazoDesde solo con plazo de presentación a partir de este día (incluido)
 * @param plazoHasta solo con plazo de presentación hasta este día (incluido)
 * @param organoId solo de este órgano de contratación
 * @param soloPlazoAbierto solo las que aún admiten ofertas: plazo sin cerrar o sin plazo publicado (el estado «publicada»
 *     no basta: la Plataforma no lo cambia hasta que se valoran las ofertas)
 * @param soloFondosUe solo con financiación de la Unión Europea
 * @param incluirAnuladas incluir las que la Plataforma ha retirado
 */
public record FiltroDeBusqueda(
        String texto,
        List<String> estados,
        List<String> tiposContrato,
        List<String> procedimientos,
        List<String> nuts,
        List<String> cpv,
        BigDecimal importeMinimo,
        BigDecimal importeMaximo,
        LocalDate plazoDesde,
        LocalDate plazoHasta,
        LocalDate publicadaDesde,
        LocalDate publicadaHasta,
        Long organoId,
        boolean soloPlazoAbierto,
        boolean soloFondosUe,
        boolean incluirAnuladas) {

    public FiltroDeBusqueda {
        texto = texto == null || texto.isBlank() ? null : texto.strip();
        estados = copia(estados);
        tiposContrato = copia(tiposContrato);
        procedimientos = copia(procedimientos);
        nuts = copia(nuts);
        cpv = copia(cpv);
    }

    public static FiltroDeBusqueda vacio() {
        return new FiltroDeBusqueda(
                null, null, null, null, null, null, null, null, null, null, null, null, null, false, false, false);
    }

    private static List<String> copia(List<String> valores) {
        return valores == null
                ? List.of()
                : valores.stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(v -> !v.isEmpty())
                        .distinct()
                        .toList();
    }
}
