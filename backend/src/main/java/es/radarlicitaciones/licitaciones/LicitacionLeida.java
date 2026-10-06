package es.radarlicitaciones.licitaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Una versión de una licitación tal como la publica la Plataforma de Contratación, ya interpretada. Es lo que la
 * ingesta entrega a este módulo para guardar: solo se queda si es más reciente que la versión guardada.
 *
 * @param id número de la licitación en la Plataforma (final del {@code <id>} de la entrada ATOM)
 * @param expediente número de expediente del órgano de contratación
 * @param url página de la licitación en la Plataforma
 * @param estado código CODICE del estado (PUB, EV, ADJ...)
 * @param importeSinIva presupuesto base de licitación sin impuestos
 * @param valorEstimado valor estimado del contrato (incluye prórrogas y modificaciones previstas)
 * @param cpv códigos CPV de la licitación y de sus lotes, sin repetir
 * @param nuts código NUTS del lugar de ejecución
 * @param plazoPresentacion fin del plazo de presentación de ofertas, si lo hay
 * @param fechaPublicacion primera fecha en la que se publicó el anuncio de licitación
 * @param actualizadaEn momento de esta versión ({@code <updated>} de la entrada)
 */
public record LicitacionLeida(
        long id,
        String expediente,
        String titulo,
        String url,
        String estado,
        String tipoContrato,
        String subtipoContrato,
        String procedimiento,
        OrganoLeido organo,
        BigDecimal importeSinIva,
        BigDecimal importeConIva,
        BigDecimal valorEstimado,
        List<String> cpv,
        String nuts,
        String lugar,
        OffsetDateTime plazoPresentacion,
        LocalDate fechaPublicacion,
        String duracion,
        boolean financiacionUe,
        List<LoteLeido> lotes,
        List<ResultadoLeido> resultados,
        List<DocumentoLeido> documentos,
        OffsetDateTime actualizadaEn) {

    public LicitacionLeida {
        Objects.requireNonNull(expediente, "expediente");
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(organo, "organo");
        Objects.requireNonNull(actualizadaEn, "actualizadaEn");
        cpv = List.copyOf(cpv);
        lotes = List.copyOf(lotes);
        resultados = List.copyOf(resultados);
        documentos = List.copyOf(documentos);
    }

    /** Un lote de la licitación. */
    public record LoteLeido(String numero, String objeto, BigDecimal importeSinIva, List<String> cpv, String nuts) {
        public LoteLeido {
            Objects.requireNonNull(numero, "numero");
            cpv = List.copyOf(cpv);
        }
    }

    /**
     * El resultado de la licitación (o de uno de sus lotes): adjudicada, desierta, desistida...
     *
     * @param lote número del lote al que se refiere, o nada si es de la licitación entera
     * @param codigo código CODICE del resultado
     */
    public record ResultadoLeido(
            String lote,
            String codigo,
            LocalDate fechaAdjudicacion,
            Integer ofertasRecibidas,
            String adjudicatario,
            String adjudicatarioNif,
            BigDecimal importeSinIva,
            BigDecimal importeConIva,
            Boolean pyme) {}

    /** Un documento publicado: pliegos y otros. Solo se guarda el enlace, nunca el fichero. */
    public record DocumentoLeido(TipoDocumento tipo, String nombre, String url) {
        public DocumentoLeido {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(url, "url");
        }
    }

    public enum TipoDocumento {
        PLIEGO_ADMINISTRATIVO,
        PLIEGO_TECNICO,
        OTRO
    }
}
