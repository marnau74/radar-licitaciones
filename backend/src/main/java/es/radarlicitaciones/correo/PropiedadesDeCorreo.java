package es.radarlicitaciones.correo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del correo ({@code radar.correo.*}).
 *
 * @param remitente dirección From de los avisos
 * @param urlWeb dirección pública de la web, para los enlaces de los correos
 * @param envioActivo si el proceso de envío está en marcha
 * @param intervalo cada cuánto se mira la bandeja de salida
 */
@ConfigurationProperties("radar.correo")
@Validated
public record PropiedadesDeCorreo(
        @NotBlank String remitente,
        @NotBlank String urlWeb,
        boolean envioActivo,
        @NotNull Duration intervalo) {}
