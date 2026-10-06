package es.radarlicitaciones.licitaciones;

import es.radarlicitaciones.compartido.Pagina;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Consultas sobre las licitaciones guardadas. */
public interface BuscadorDeLicitaciones {

    int TAMANO_MAXIMO = 100;
    /** Más allá no se pagina: hay que afinar la búsqueda (paginar muy hondo es caro y nadie lo usa). */
    int RESULTADOS_PAGINABLES = 10_000;

    /** Una página de resultados ({@code pagina} empieza en 1). */
    Pagina<LicitacionResumen> buscar(FiltroDeBusqueda filtro, OrdenDeBusqueda orden, int pagina, int tamano);

    /**
     * Licitaciones en plazo que encajan con el filtro y han cambiado en una ingesta: lo que se avisa a cada alerta.
     *
     * @param ingeridasDesde inicio de la ingesta
     * @param ingeridasHasta fin de la ingesta
     * @param publicadasDesde no se avisa de licitaciones publicadas antes de este día
     * @return números de licitación, como mucho {@code limite}
     */
    List<Long> novedades(
            FiltroDeBusqueda filtro,
            Instant ingeridasDesde,
            Instant ingeridasHasta,
            LocalDate publicadasDesde,
            int limite);

    /** Los resúmenes de estas licitaciones, en el orden recibido; las que no existen se omiten. */
    List<LicitacionResumen> porIds(Collection<Long> ids);
}
