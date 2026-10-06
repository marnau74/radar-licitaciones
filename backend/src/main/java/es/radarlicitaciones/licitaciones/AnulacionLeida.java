package es.radarlicitaciones.licitaciones;

import java.time.OffsetDateTime;

/** La fuente retira una licitación ({@code at:deleted-entry}): se marca como anulada si no hay una versión posterior. */
public record AnulacionLeida(long id, OffsetDateTime cuando) {}
