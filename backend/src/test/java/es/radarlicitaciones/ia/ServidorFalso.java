package es.radarlicitaciones.ia;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Servidor HTTP de pruebas (el del JDK): responde siempre lo mismo y apunta las peticiones que recibe. Sirve para
 * simular la API de Anthropic y la de Ollama sin red ni claves.
 */
final class ServidorFalso implements AutoCloseable {

    record Peticion(String metodo, String ruta, Headers cabeceras, String cuerpo) {}

    private final HttpServer servidor;
    private final List<Peticion> peticiones = new ArrayList<>();
    private volatile int codigo = 200;
    private volatile String respuesta = "{}";

    ServidorFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/", intercambio -> {
            var cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (peticiones) {
                peticiones.add(new Peticion(
                        intercambio.getRequestMethod(),
                        intercambio.getRequestURI().getPath(),
                        intercambio.getRequestHeaders(),
                        cuerpo));
            }
            var bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(codigo, bytes.length);
            try (var salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
    }

    ServidorFalso responder(int codigo, String respuesta) {
        this.codigo = codigo;
        this.respuesta = respuesta;
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    List<Peticion> peticiones() {
        synchronized (peticiones) {
            return List.copyOf(peticiones);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
