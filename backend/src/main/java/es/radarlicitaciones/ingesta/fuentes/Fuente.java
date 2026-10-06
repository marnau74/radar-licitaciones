package es.radarlicitaciones.ingesta.fuentes;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * De dónde salen los ficheros ATOM de una ingesta: el feed vivo (páginas encadenadas), un paquete ZIP o ficheros
 * locales. Los ficheros se piden de uno en uno y por orden.
 */
public interface Fuente extends AutoCloseable {

    /** Para los registros y el resumen de la ingesta. */
    String descripcion();

    /**
     * El fichero en la posición {@code indice} (desde 0), o nada si se ha terminado.
     *
     * @param anterior cómo fue la lectura del fichero anterior; nulo para el primero o al reanudar
     */
    Optional<FicheroDeFeed> fichero(int indice, FicheroLeido anterior);

    /**
     * Si una ingesta fallida puede continuar donde se quedó. Solo las fuentes fijas (un ZIP, unos ficheros): el feed
     * vivo cambia cada pocos minutos y lo que hay que hacer es volver a empezar desde la marca.
     */
    boolean reanudable();

    @Override
    default void close() {}

    /** Un fichero ATOM: su nombre y cómo abrirlo. */
    record FicheroDeFeed(String nombre, Abridor abridor) {

        public InputStream abrir() throws IOException {
            return abridor.abrir();
        }
    }

    @FunctionalInterface
    interface Abridor {
        InputStream abrir() throws IOException;
    }

    /**
     * Lo que se sabe de un fichero ya leído entero.
     *
     * @param enlaceSiguiente el {@code <link rel="next">} del fichero (la página anterior en el tiempo)
     * @param masAntiguo la fecha más antigua de sus entradas, si tenía alguna
     */
    record FicheroLeido(Optional<String> enlaceSiguiente, OffsetDateTime masAntiguo) {}
}
