package es.radarlicitaciones.licitaciones.web;

import es.radarlicitaciones.licitaciones.FiltroDeBusqueda;
import es.radarlicitaciones.licitaciones.OrdenDeBusqueda;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Parámetros de la búsqueda tal como llegan en la URL. Las listas admiten el parámetro repetido ({@code estado=PUB&estado=EV})
 * o separado por comas ({@code estado=PUB,EV}).
 */
record ParametrosDeBusqueda(
        @Schema(description = "Palabras a buscar; admite \"frases\" y -exclusiones") @Size(max = 200)
        String q,

        @Schema(description = "Estados (PUB, EV, ADJ, RES, ANUL, PRE)") @Size(max = 10)
        List<@Pattern(regexp = "[A-Z]{2,5}") String> estado,

        @Schema(description = "Tipos de contrato (1 suministros, 2 servicios, 3 obras...)") @Size(max = 20)
        List<@Pattern(regexp = "\\d{1,3}") String> tipo,

        @Schema(description = "Procedimientos (1 abierto, 9 abierto simplificado...)") @Size(max = 20)
        List<@Pattern(regexp = "\\d{1,3}") String> procedimiento,

        @Schema(description = "Regiones NUTS o prefijos (ES51 Cataluña, ES511 Barcelona)") @Size(max = 30)
        List<@Pattern(regexp = "[A-Z]{2}[0-9A-Z]{0,3}") String> nuts,

        @Schema(description = "Códigos CPV o prefijos (72 servicios de TI)") @Size(max = 30)
        List<@Pattern(regexp = "\\d{2,8}") String> cpv,

        @Schema(description = "Importe mínimo sin IVA") @PositiveOrZero
        BigDecimal importeMin,

        @Schema(description = "Importe máximo sin IVA") @PositiveOrZero
        BigDecimal importeMax,

        @Schema(description = "Plazo de presentación desde este día")
        LocalDate plazoDesde,

        @Schema(description = "Plazo de presentación hasta este día")
        LocalDate plazoHasta,

        @Schema(description = "Publicadas desde este día") LocalDate publicadaDesde,
        @Schema(description = "Publicadas hasta este día") LocalDate publicadaHasta,

        @Schema(description = "Órgano de contratación") @Positive
        Long organo,

        @Schema(description = "Solo las que aún admiten ofertas (plazo sin cerrar o sin plazo publicado)")
        Boolean abiertas,

        @Schema(description = "Solo con fondos de la UE") Boolean fondosUe,
        @Schema(description = "Incluir las anuladas") Boolean incluirAnuladas,

        @Schema(
                description = "Orden",
                allowableValues = {"relevancia", "publicacion", "plazo", "importe"},
                defaultValue = "relevancia")
        @Pattern(regexp = "relevancia|publicacion|plazo|importe")
        String orden,

        @Schema(defaultValue = "1") @Min(1) @Max(10_000) Integer pagina,
        @Schema(defaultValue = "20") @Min(1) @Max(100) Integer tamano) {

    FiltroDeBusqueda filtro() {
        return new FiltroDeBusqueda(
                q,
                estado,
                tipo,
                procedimiento,
                nuts,
                cpv,
                importeMin,
                importeMax,
                plazoDesde,
                plazoHasta,
                publicadaDesde,
                publicadaHasta,
                organo,
                Boolean.TRUE.equals(abiertas),
                Boolean.TRUE.equals(fondosUe),
                Boolean.TRUE.equals(incluirAnuladas));
    }

    OrdenDeBusqueda ordenDeBusqueda() {
        return orden == null ? OrdenDeBusqueda.RELEVANCIA : OrdenDeBusqueda.valueOf(orden.toUpperCase(Locale.ROOT));
    }

    int paginaPedida() {
        return pagina == null ? 1 : pagina;
    }

    int tamanoPedido() {
        return tamano == null ? 20 : tamano;
    }
}
