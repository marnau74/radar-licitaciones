package es.radarlicitaciones.licitaciones;

/**
 * Un documento publicado con la licitación (pliegos, anuncios...).
 *
 * @param tipo {@code PLIEGO_ADMINISTRATIVO}, {@code PLIEGO_TECNICO} u {@code OTRO}
 */
public record DocumentoDeLicitacion(String tipo, String nombre, String url) {}
