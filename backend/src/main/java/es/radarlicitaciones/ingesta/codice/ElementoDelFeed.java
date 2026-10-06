package es.radarlicitaciones.ingesta.codice;

import es.radarlicitaciones.licitaciones.AnulacionLeida;
import es.radarlicitaciones.licitaciones.LicitacionLeida;

/** Lo que trae un fichero del feed: versiones de licitaciones y anulaciones. */
public sealed interface ElementoDelFeed {

    record Licitacion(LicitacionLeida licitacion) implements ElementoDelFeed {}

    record Anulacion(AnulacionLeida anulacion) implements ElementoDelFeed {}

    /** Una entrada que no se ha podido interpretar. Se cuenta y se anota, pero no para la ingesta. */
    record Ilegible(String idEntrada, String motivo) implements ElementoDelFeed {}
}
