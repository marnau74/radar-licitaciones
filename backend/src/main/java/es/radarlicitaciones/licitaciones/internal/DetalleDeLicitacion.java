package es.radarlicitaciones.licitaciones.internal;

import es.radarlicitaciones.catalogos.Codigo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Todo lo que se sabe de una licitación, con los códigos ya traducidos a su nombre. */
public record DetalleDeLicitacion(
        long id,
        String expediente,
        String titulo,
        String url,
        Codigo estado,
        Codigo tipoContrato,
        String subtipoContrato,
        Codigo procedimiento,
        Organo organo,
        BigDecimal importeSinIva,
        BigDecimal importeConIva,
        BigDecimal valorEstimado,
        List<Codigo> cpv,
        Codigo nuts,
        String lugar,
        OffsetDateTime plazoPresentacion,
        LocalDate fechaPublicacion,
        String duracion,
        boolean financiacionUe,
        boolean anulada,
        List<Lote> lotes,
        List<Resultado> resultados,
        List<Documento> documentos,
        OffsetDateTime actualizadaEn,
        OffsetDateTime vistaPorPrimeraVez) {

    public record Organo(
            long id,
            String nombre,
            String nif,
            String dir3,
            Codigo tipo,
            String ciudad,
            String codigoPostal,
            String web,
            String perfilContratante,
            List<String> jerarquia) {}

    public record Lote(String numero, String objeto, BigDecimal importeSinIva, List<Codigo> cpv, Codigo nuts) {}

    public record Resultado(
            String lote,
            Codigo resultado,
            LocalDate fechaAdjudicacion,
            Integer ofertasRecibidas,
            String adjudicatario,
            String adjudicatarioNif,
            BigDecimal importeSinIva,
            BigDecimal importeConIva,
            Boolean pyme) {}

    public record Documento(String tipo, String nombre, String url) {}
}
