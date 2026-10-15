package es.radarlicitaciones.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Qué modelo se usa, según {@code radar.ia.resumenes.proveedor} y {@code radar.ia.embeddings.proveedor}. */
@Configuration(proxyBeanMethods = false)
class ConfiguracionDeIa {

    @Bean
    @ConditionalOnProperty(name = "radar.ia.resumenes.proveedor", havingValue = "claude")
    ModeloDeLenguaje modeloClaude(PropiedadesDeIa propiedades) {
        return new ModeloClaude(propiedades.claude());
    }

    @Bean
    @ConditionalOnProperty(name = "radar.ia.resumenes.proveedor", havingValue = "ollama")
    ModeloDeLenguaje modeloOllama(PropiedadesDeIa propiedades, JsonMapper json) {
        return new ModeloOllama(propiedades.ollama(), propiedades.pliegos().maxPaginas(), json);
    }

    @Bean
    @ConditionalOnProperty(name = "radar.ia.embeddings.proveedor", havingValue = "ollama")
    Vectorizador vectorizadorOllama(PropiedadesDeIa propiedades, JsonMapper json) {
        return new Vectorizador.Ollama(propiedades, json);
    }
}
