package es.radarlicitaciones.ia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** El modelo que lee el pliego y devuelve el resumen (Claude u Ollama, según la configuración). */
interface ModeloDeLenguaje {

    /**
     * @param pdf el pliego tal cual
     * @param contexto de qué licitación es (título, órgano, tipo de documento): ayuda a interpretar el pliego
     */
    Resultado resumir(byte[] pdf, String contexto);

    /** Proveedor y modelo (p. ej. {@code claude/claude-opus-5-5}): se guarda con cada resumen. */
    String descripcion();

    /**
     * @param aviso algo que conviene saber al leerlo (p. ej. que el modelo local solo leyó una parte), o null
     */
    record Resultado(ResumenDePliego resumen, String modelo, String aviso) {}

    /** Las instrucciones para el modelo, iguales para todos los proveedores. */
    static String instrucciones() {
        try {
            return new ClassPathResource("ia/instrucciones-resumen.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
