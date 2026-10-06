package es.radarlicitaciones.licitaciones;

/** Orden de los resultados. Siempre se desempata por número de licitación, para que paginar sea estable. */
public enum OrdenDeBusqueda {
    /** Las que mejor encajan con el texto; sin texto, como {@link #PUBLICACION}. */
    RELEVANCIA,
    /** Las publicadas más recientemente primero. */
    PUBLICACION,
    /** Las que antes cierran el plazo primero; las que no tienen plazo, al final. */
    PLAZO,
    /** Las de mayor importe primero. */
    IMPORTE
}
