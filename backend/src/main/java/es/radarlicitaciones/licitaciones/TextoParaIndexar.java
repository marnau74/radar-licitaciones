package es.radarlicitaciones.licitaciones;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Lo que describe una licitación en palabras (título, órgano, actividades CPV, objeto de los lotes y lugar), para la
 * búsqueda por significado, con lo que hace falta para filtrar sin volver a la tabla.
 *
 * @param ingeridaEn cuándo se guardó esta versión (para recorrerlas en orden)
 */
public record TextoParaIndexar(
        long id, Instant ingeridaEn, OffsetDateTime plazoPresentacion, boolean anulada, String texto) {}
