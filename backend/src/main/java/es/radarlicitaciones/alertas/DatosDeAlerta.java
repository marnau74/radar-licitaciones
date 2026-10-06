package es.radarlicitaciones.alertas;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que el usuario rellena al crear o editar una alerta. Hace falta al menos un criterio: una alerta de «todo» serían
 * cientos de avisos al día.
 *
 * @param porCorreo si además de en la web quiere el resumen por correo (por defecto sí)
 * @param activa si se avisa (por defecto sí); una alerta pausada se conserva
 */
record DatosDeAlerta(
        @NotBlank @Size(max = 80) String nombre,
        @Size(max = 200) String texto,
        @Size(max = 20) List<@Pattern(regexp = "\\d{1,3}") String> tiposContrato,
        @Size(max = 20) List<@Pattern(regexp = "\\d{1,3}") String> procedimientos,
        @Size(max = 30) List<@Pattern(regexp = "[A-Z]{2}[0-9A-Z]{0,3}") String> nuts,
        @Size(max = 30) List<@Pattern(regexp = "\\d{2,8}") String> cpv,
        @PositiveOrZero BigDecimal importeMinimo,
        @PositiveOrZero BigDecimal importeMaximo,
        Boolean soloFondosUe,
        Boolean porCorreo,
        Boolean activa) {

    @AssertTrue(message = "Indica al menos un criterio: texto, tipo, procedimiento, lugar, CPV o importe")
    boolean isConAlgunCriterio() {
        return (texto != null && !texto.isBlank())
                || noVacia(tiposContrato)
                || noVacia(procedimientos)
                || noVacia(nuts)
                || noVacia(cpv)
                || importeMinimo != null
                || importeMaximo != null;
    }

    @AssertTrue(message = "El importe mínimo no puede ser mayor que el máximo")
    boolean isImportesCoherentes() {
        return importeMinimo == null || importeMaximo == null || importeMinimo.compareTo(importeMaximo) <= 0;
    }

    private static boolean noVacia(List<String> lista) {
        return lista != null && !lista.isEmpty();
    }
}
