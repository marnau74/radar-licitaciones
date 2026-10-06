package es.radarlicitaciones.ingesta.fuentes;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El feed vivo: la primera página tiene las 500 entradas más recientes y cada una enlaza con la anterior en el tiempo.
 * Se retrocede hasta pasar la marca (lo más reciente que se leyó en la última ingesta completada). Sin marca, solo se
 * leen unas pocas páginas: el histórico se carga con los paquetes.
 */
public class FeedVivo implements Fuente {

    private static final Logger log = LoggerFactory.getLogger(FeedVivo.class);

    private final Descargador descargador;
    private final URI primeraPagina;
    private final OffsetDateTime marca;
    private final int maxPaginas;

    /**
     * @param marca hasta dónde se leyó la última vez, o nulo si es la primera
     * @param maxPaginas páginas como mucho (con marca, un tope de seguridad; sin ella, lo que se lee)
     */
    public FeedVivo(Descargador descargador, URI primeraPagina, OffsetDateTime marca, int maxPaginas) {
        this.descargador = descargador;
        this.primeraPagina = primeraPagina;
        this.marca = marca;
        this.maxPaginas = maxPaginas;
    }

    @Override
    public String descripcion() {
        return marca == null ? "Feed vivo (primera lectura)" : "Feed vivo desde " + marca;
    }

    @Override
    public Optional<FicheroDeFeed> fichero(int indice, FicheroLeido anterior) {
        if (indice == 0) {
            return Optional.of(pagina(primeraPagina));
        }
        if (indice >= maxPaginas) {
            if (marca != null) {
                log.warn("Se ha llegado al tope de {} páginas sin alcanzar la marca {}", maxPaginas, marca);
            }
            return Optional.empty();
        }
        if (anterior == null || anterior.enlaceSiguiente().isEmpty()) {
            return Optional.empty();
        }
        // Las entradas vienen de la más reciente a la más antigua: si la página ya tenía alguna anterior a la marca,
        // todo lo que queda detrás ya se leyó.
        if (marca != null
                && (anterior.masAntiguo() == null || anterior.masAntiguo().isBefore(marca))) {
            return Optional.empty();
        }
        return Optional.of(pagina(URI.create(anterior.enlaceSiguiente().get())));
    }

    private FicheroDeFeed pagina(URI uri) {
        return new FicheroDeFeed(uri.toString(), () -> descargador.abrir(uri));
    }

    @Override
    public boolean reanudable() {
        return false;
    }
}
