package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.ingesta.fuentes.Descargador;
import es.radarlicitaciones.ingesta.fuentes.FeedVivo;
import es.radarlicitaciones.ingesta.fuentes.Fuente;
import es.radarlicitaciones.ingesta.fuentes.FuenteDeFicheros;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Crea la fuente de cada ingesta a partir de los parámetros del job. */
@Component
class FabricaDeFuentes {

    private static final Logger log = LoggerFactory.getLogger(FabricaDeFuentes.class);

    private final PropiedadesDeIngesta propiedades;
    private final Descargador descargador;
    private final MarcaDelFeed marca;

    FabricaDeFuentes(PropiedadesDeIngesta propiedades, MarcaDelFeed marca) {
        this.propiedades = propiedades;
        this.descargador = new Descargador(propiedades.hostsPermitidos(), propiedades.tiempoMaximoPeticion());
        this.marca = marca;
    }

    Fuente crear(String origen, String valor) {
        return switch (origen) {
            case "feed" -> {
                var ultima = marca.leer().orElse(null);
                var paginas = ultima == null ? propiedades.paginasFeedSinMarca() : propiedades.maxPaginasFeed();
                yield new FeedVivo(descargador, propiedades.feedUrl(), ultima, paginas);
            }
            case "paquete" -> FuenteDeFicheros.de(paquete(valor), "Paquete " + valor);
            case "fichero" ->
                FuenteDeFicheros.de(Path.of(valor), "Fichero " + Path.of(valor).getFileName());
            default -> throw new IllegalArgumentException("Origen desconocido: " + origen);
        };
    }

    /**
     * Descarga el paquete si hace falta. Los de meses y años cerrados no cambian y se reutilizan; el del mes en curso
     * se vuelve a pedir si el guardado es de otro día.
     */
    private Path paquete(String periodo) {
        var url = URI.create(propiedades.paquetesUrl().replace("{periodo}", periodo));
        var nombre = Path.of(url.getPath()).getFileName().toString();
        var destino = propiedades.carpetaDeDescargas().resolve(nombre);
        try {
            if (Files.exists(destino) && !caducado(periodo, destino)) {
                log.info("Se reutiliza el paquete ya descargado {}", destino);
                return destino;
            }
            log.info("Descargando {} en {}", url, destino);
            descargador.descargar(url, destino);
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido descargar " + url, e);
        }
    }

    private boolean caducado(String periodo, Path fichero) throws IOException {
        var actual = YearMonth.now(propiedades.zona());
        var esActual = periodo.equals(actual.format(DateTimeFormatter.ofPattern("yyyyMM")))
                || periodo.equals(String.valueOf(actual.getYear()));
        if (!esActual) {
            return false;
        }
        var modificado = Files.getLastModifiedTime(fichero)
                .toInstant()
                .atZone(propiedades.zona())
                .toLocalDate();
        return modificado.isBefore(java.time.LocalDate.now(propiedades.zona()));
    }
}
