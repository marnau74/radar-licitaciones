package es.radarlicitaciones.ia;

import java.time.OffsetDateTime;

/**
 * El resumen de un documento de una licitación, tal como se guarda y se devuelve.
 *
 * @param modelo proveedor y modelo que lo hicieron
 * @param aviso algo que conviene saber al leerlo, o null
 */
public record ResumenGuardado(
        long licitacionId,
        String documentoUrl,
        String documentoNombre,
        String modelo,
        ResumenDePliego resumen,
        String aviso,
        OffsetDateTime creadoEn) {}
