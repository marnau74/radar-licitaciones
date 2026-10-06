package es.radarlicitaciones.ingesta.fuentes;

import es.radarlicitaciones.ingesta.codice.FicheroNoValido;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Peticiones HTTP a la Plataforma. Solo a los servidores permitidos y por HTTPS: las páginas del feed traen el enlace a
 * la siguiente y no se sigue un enlace a cualquier sitio (SSRF). Los fallos de red y los 5xx se reintentan tres veces.
 */
public class Descargador {

    private static final Logger log = LoggerFactory.getLogger(Descargador.class);
    private static final int INTENTOS = 3;
    private static final String AGENTE = "radar-licitaciones (+https://github.com/marnau74/radar-licitaciones)";

    private final HttpClient http;
    private final List<String> hostsPermitidos;
    private final Duration tiempoMaximo;
    private final Duration esperaEntreIntentos;

    public Descargador(List<String> hostsPermitidos, Duration tiempoMaximo) {
        this(hostsPermitidos, tiempoMaximo, Duration.ofSeconds(5));
    }

    Descargador(List<String> hostsPermitidos, Duration tiempoMaximo, Duration esperaEntreIntentos) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.hostsPermitidos =
                hostsPermitidos.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
        this.tiempoMaximo = tiempoMaximo;
        this.esperaEntreIntentos = esperaEntreIntentos;
    }

    /** Abre la respuesta como flujo, para leerla mientras llega (una página del feed son unos 12 MB). */
    public InputStream abrir(URI uri) throws IOException {
        return pedir(uri, HttpResponse.BodyHandlers.ofInputStream());
    }

    /** Descarga a un fichero. Se escribe en uno temporal y se renombra al terminar: nunca queda uno a medias. */
    public void descargar(URI uri, Path destino) throws IOException {
        Files.createDirectories(destino.toAbsolutePath().getParent());
        var parcial = destino.resolveSibling(destino.getFileName() + ".parcial");
        pedir(uri, HttpResponse.BodyHandlers.ofFile(parcial));
        Files.move(parcial, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    void comprobarPermitido(URI uri) {
        var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !hostsPermitidos.contains(host)) {
            throw new FicheroNoValido("No se descarga de " + uri + ": solo HTTPS y de " + hostsPermitidos);
        }
    }

    private <T> T pedir(URI uri, HttpResponse.BodyHandler<T> manejador) throws IOException {
        comprobarPermitido(uri);
        var peticion = HttpRequest.newBuilder(uri)
                .timeout(tiempoMaximo)
                .header("User-Agent", AGENTE)
                .GET()
                .build();
        IOException ultimoFallo = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                var respuesta = http.send(peticion, manejador);
                comprobarPermitido(respuesta.uri()); // tras las redirecciones
                if (respuesta.statusCode() == 200) {
                    return respuesta.body();
                }
                cerrar(respuesta.body());
                ultimoFallo = new IOException("HTTP " + respuesta.statusCode() + " al pedir " + uri);
                if (respuesta.statusCode() < 500) {
                    break; // un 404 no se arregla reintentando
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Descarga interrumpida: " + uri);
            } catch (IOException e) {
                ultimoFallo = e;
            }
            if (intento < INTENTOS) {
                log.warn("Fallo al pedir {} (intento {} de {}): {}", uri, intento, INTENTOS, ultimoFallo.getMessage());
                esperar(esperaEntreIntentos.multipliedBy(intento));
            }
        }
        throw ultimoFallo;
    }

    private static void cerrar(Object cuerpo) {
        if (cuerpo instanceof InputStream flujo) {
            try {
                flujo.close();
            } catch (IOException e) {
                // Ya se va a informar del fallo de la petición.
            }
        }
    }

    private static void esperar(Duration espera) throws InterruptedIOException {
        try {
            Thread.sleep(espera);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Descarga interrumpida");
        }
    }
}
