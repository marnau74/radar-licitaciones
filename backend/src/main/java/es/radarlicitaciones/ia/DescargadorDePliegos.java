package es.radarlicitaciones.ia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Descarga el PDF de un pliego. Las direcciones vienen del feed, así que solo se aceptan HTTPS y los dominios de la
 * Plataforma de Contratación (también en cada redirección, que se siguen a mano): nadie puede usar el resumen para que
 * el servidor haga peticiones a su red interna o a cualquier sitio. El tamaño se corta al leer, no se fía de la
 * cabecera.
 */
@Component
class DescargadorDePliegos {

    private static final int MAX_REDIRECCIONES = 5;

    private final PropiedadesDeIa.Pliegos config;
    private final HttpClient http;

    DescargadorDePliegos(PropiedadesDeIa propiedades) {
        this.config = propiedades.pliegos();
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(config.tiempoMaximo())
                .build();
    }

    byte[] descargar(String url) {
        var uri = permitida(url);
        try {
            for (var i = 0; i <= MAX_REDIRECCIONES; i++) {
                var peticion = HttpRequest.newBuilder(uri)
                        .timeout(config.tiempoMaximo())
                        .header("User-Agent", "radar-licitaciones")
                        .GET()
                        .build();
                var respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
                try (var cuerpo = respuesta.body()) {
                    var codigo = respuesta.statusCode();
                    if (codigo >= 300 && codigo < 400) {
                        var destino = respuesta.headers().firstValue("Location").orElse(null);
                        if (destino == null) {
                            throw ErrorDeIa.pliegoNoValido("La Plataforma redirige el pliego sin decir adónde");
                        }
                        uri = permitida(uri.resolve(destino).toString());
                        continue;
                    }
                    if (codigo != 200) {
                        throw ErrorDeIa.servicioExterno(
                                "La Plataforma de Contratación respondió " + codigo + " al pedir el pliego", null);
                    }
                    return pdf(cuerpo);
                }
            }
            throw ErrorDeIa.pliegoNoValido("Demasiadas redirecciones al descargar el pliego");
        } catch (IOException e) {
            throw ErrorDeIa.servicioExterno("No se pudo descargar el pliego de la Plataforma de Contratación", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorDeIa.servicioExterno("Descarga del pliego interrumpida", e);
        }
    }

    /** HTTPS y un dominio de la lista (o un subdominio suyo). */
    URI permitida(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw ErrorDeIa.pliegoNoValido("La dirección del pliego no es válida");
        }
        var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        var dominioPermitido = config.dominios().stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !dominioPermitido || uri.getUserInfo() != null) {
            throw ErrorDeIa.pliegoNoValido("Solo se resumen pliegos publicados en la Plataforma de Contratación");
        }
        return uri;
    }

    private byte[] pdf(InputStream cuerpo) throws IOException {
        var maximo = (long) config.maxMegas() * 1024 * 1024;
        var salida = new ByteArrayOutputStream();
        var buffer = new byte[64 * 1024];
        long total = 0;
        int leidos;
        while ((leidos = cuerpo.read(buffer)) != -1) {
            total += leidos;
            if (total > maximo) {
                throw ErrorDeIa.pliegoNoValido("El pliego pesa más de " + config.maxMegas() + " MB");
            }
            salida.write(buffer, 0, leidos);
        }
        var bytes = salida.toByteArray();
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw ErrorDeIa.pliegoNoValido("El documento no es un PDF: solo se resumen pliegos en PDF");
        }
        return bytes;
    }
}
