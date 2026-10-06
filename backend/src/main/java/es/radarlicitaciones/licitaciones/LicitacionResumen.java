package es.radarlicitaciones.licitaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Lo que se enseña de una licitación en una lista de resultados. */
public record LicitacionResumen(
        long id,
        String expediente,
        String titulo,
        String estado,
        String tipoContrato,
        String procedimiento,
        long organoId,
        String organo,
        BigDecimal importeSinIva,
        OffsetDateTime plazoPresentacion,
        LocalDate fechaPublicacion,
        String nuts,
        String lugar,
        List<String> cpv,
        int numLotes,
        boolean financiacionUe,
        boolean anulada) {

    public LicitacionResumen {
        cpv = List.copyOf(cpv);
    }
}
