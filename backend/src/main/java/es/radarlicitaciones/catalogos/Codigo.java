package es.radarlicitaciones.catalogos;

/**
 * Un código con su nombre. Si el código no está en el catálogo (la Plataforma añade alguno de vez en cuando), el nombre
 * es el propio código: se enseña tal cual en vez de perderlo.
 */
public record Codigo(String codigo, String nombre) {}
