package es.radarlicitaciones.ia;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la IA ({@code radar.ia.*}). Todo está apagado si no se configura: sin proveedor no hay resúmenes y
 * sin embeddings no hay búsqueda por significado.
 *
 * @param resumenes con qué se resumen los pliegos
 * @param embeddings con qué se calculan los vectores de la búsqueda por significado
 */
@ConfigurationProperties("radar.ia")
record PropiedadesDeIa(
        @DefaultValue Resumenes resumenes,
        @DefaultValue Embeddings embeddings,
        @DefaultValue Claude claude,
        @DefaultValue Ollama ollama,
        @DefaultValue Pliegos pliegos) {

    enum ProveedorDeResumenes {
        NINGUNO,
        CLAUDE,
        OLLAMA
    }

    enum ProveedorDeEmbeddings {
        NINGUNO,
        OLLAMA
    }

    /**
     * @param porUsuarioYDia resúmenes nuevos que puede pedir cada usuario al día (los ya hechos no cuentan)
     */
    record Resumenes(
            @DefaultValue("ninguno") ProveedorDeResumenes proveedor,
            @DefaultValue("10") int porUsuarioYDia) {}

    /**
     * @param dimensiones las del modelo; la columna de la base de datos es {@code vector(1024)}
     * @param porLote licitaciones que se vectorizan en cada petición al modelo
     */
    record Embeddings(
            @DefaultValue("ninguno") ProveedorDeEmbeddings proveedor,
            @DefaultValue("1024") int dimensiones,
            @DefaultValue("32") int porLote) {}

    /**
     * @param claveApi clave de la API de Anthropic (variable {@code ANTHROPIC_API_KEY})
     * @param esfuerzo {@code low}, {@code medium}, {@code high}...: cuánto razona el modelo antes de contestar
     */
    record Claude(
            String claveApi,
            @DefaultValue("claude-opus-5-5") String modelo,
            @DefaultValue("medium") String esfuerzo,
            @DefaultValue("16000") long maxTokens,
            @DefaultValue("PT5M") Duration tiempoMaximo,
            String urlBase) {}

    /**
     * @param contexto tokens de contexto que se piden a Ollama (por defecto usa 2048, poco para un pliego)
     */
    record Ollama(
            @DefaultValue("http://localhost:11434") URI url,
            @DefaultValue("qwen3:8b") String modeloTexto,
            @DefaultValue("bge-m3") String modeloEmbeddings,
            @DefaultValue("32768") int contexto,
            @DefaultValue("PT10M") Duration tiempoMaximo) {}

    /**
     * @param dominios solo se descargan pliegos de estos dominios (y sus subdominios), por HTTPS: las direcciones
     *     vienen del feed y no se deja que lleven a cualquier sitio
     * @param maxMegas los PDF más grandes no se resumen
     * @param maxPaginas para los modelos locales, que reciben el texto: se leen como mucho estas páginas
     */
    record Pliegos(
            @DefaultValue({"contrataciondelestado.es", "contrataciondelsectorpublico.gob.es"})
            List<String> dominios,

            @DefaultValue("25") int maxMegas,
            @DefaultValue("120") int maxPaginas,
            @DefaultValue("PT1M") Duration tiempoMaximo) {}
}
