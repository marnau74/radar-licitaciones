package es.radarlicitaciones.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import java.util.Base64;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Resumen con Claude a través del SDK oficial de Anthropic. El PDF va entero (Claude lee también las tablas y el texto
 * escaneado) y la respuesta es una salida estructurada con el esquema de {@link ResumenDePliego}, así que no hay que
 * interpretar texto libre.
 *
 * <p>Si el modelo rechaza la petición por sus salvaguardas, se pide con la reserva automática del servidor
 * ({@code fallbacks: "default"}), que la pasa a otro modelo según el motivo; si aun así se rechaza, se informa.
 */
class ModeloClaude implements ModeloDeLenguaje {

    static final String BETA_RESERVA = "server-side-fallback-2026-07-01";

    private final AnthropicClient cliente;
    private final PropiedadesDeIa.Claude config;

    ModeloClaude(PropiedadesDeIa.Claude config) {
        this.config = config;
        // fromEnv(): sin clave en la configuración se usa ANTHROPIC_API_KEY (o la sesión de «ant auth login»)
        var constructor = AnthropicOkHttpClient.builder().fromEnv().timeout(config.tiempoMaximo());
        if (StringUtils.hasText(config.claveApi())) {
            constructor.apiKey(config.claveApi());
        }
        if (StringUtils.hasText(config.urlBase())) {
            constructor.baseUrl(config.urlBase());
        }
        this.cliente = constructor.build();
    }

    @Override
    public String descripcion() {
        return "claude/" + config.modelo();
    }

    @Override
    public Resultado resumir(byte[] pdf, String contexto) {
        var documento = DocumentBlockParam.builder()
                .source(Base64PdfSource.builder()
                        .data(Base64.getEncoder().encodeToString(pdf))
                        .build())
                .build();
        var peticion = MessageCreateParams.builder()
                .model(config.modelo())
                .maxTokens(config.maxTokens())
                .system(ModeloDeLenguaje.instrucciones())
                .outputConfig(StructuredOutputConfig.<ResumenDePliego>builder()
                        .format(ResumenDePliego.class)
                        .effort(OutputConfig.Effort.of(config.esfuerzo()))
                        .build())
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofDocument(documento),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text("Resume este pliego. Licitación: " + contexto)
                                .build())))
                // Reserva del servidor ante un rechazo de las salvaguardas: elige el modelo según el motivo
                .putAdditionalHeader("anthropic-beta", BETA_RESERVA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();

        try {
            var respuesta = cliente.messages().create(peticion);
            var motivo = respuesta.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(motivo)) {
                var explicacion =
                        respuesta.stopDetails().flatMap(d -> d.explanation()).orElse("sin explicación");
                throw ErrorDeIa.pliegoNoValido("El modelo no ha querido resumir este documento (" + explicacion + ")");
            }
            if (StopReason.MAX_TOKENS.equals(motivo)) {
                throw ErrorDeIa.servicioExterno("El resumen se cortó por largo: vuelve a intentarlo", null);
            }
            var resumen = respuesta.content().stream()
                    .flatMap(bloque -> bloque.text().stream())
                    .map(texto -> texto.text())
                    .findFirst()
                    .orElseThrow(() -> ErrorDeIa.servicioExterno("Claude no devolvió ningún resumen", null));
            return new Resultado(resumen, "claude/" + respuesta.model().asString(), null);
        } catch (AnthropicServiceException e) {
            // 4xx de nuestra parte (clave, modelo, documento demasiado grande) o 5xx/429 de Anthropic: el detalle va al
            // registro, al usuario un mensaje sin datos internos
            throw ErrorDeIa.servicioExterno("Claude no ha podido resumir el pliego (" + e.statusCode() + ")", e);
        } catch (AnthropicIoException e) {
            throw ErrorDeIa.servicioExterno("No se pudo conectar con la API de Anthropic", e);
        }
    }
}
