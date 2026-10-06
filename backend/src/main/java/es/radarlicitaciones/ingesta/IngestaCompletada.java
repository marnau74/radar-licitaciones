package es.radarlicitaciones.ingesta;

import java.time.Instant;

/**
 * Una ingesta ha terminado bien. Las licitaciones que han cambiado desde la anterior ingesta completada son las que
 * tienen {@code ingerida_en} entre {@code desde} y {@code hasta} (horas de la base de datos).
 *
 * <p>{@code desde} es el final de la anterior ingesta completada, no el inicio de esta: si una ingesta falla a medias,
 * lo que guardó también entra en la siguiente y nadie se queda sin aviso.
 *
 * @param ingesta número de la ingesta (id de la ejecución de Spring Batch)
 */
public record IngestaCompletada(
        long ingesta, String origen, Instant desde, Instant hasta, int nuevas, int actualizadas) {}
