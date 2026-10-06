package es.radarlicitaciones.ingesta;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la ingesta ({@code radar.ingesta.*}).
 *
 * @param feedUrl primera página del feed vivo
 * @param paquetesUrl plantilla de los paquetes históricos; {@code {periodo}} se sustituye por AAAA o AAAAMM
 * @param directorioDescargas dónde se guardan los paquetes descargados (un mes son 300 MB)
 * @param programada si la ingesta diaria se lanza sola
 * @param maxPaginasFeed tope de páginas del feed en una ingesta (cada una son 500 entradas)
 * @param paginasFeedSinMarca páginas que se leen la primera vez, cuando aún no se sabe hasta dónde se leyó
 * @param hostsPermitidos los únicos servidores a los que se descarga: el feed trae enlaces y no se sigue cualquiera
 * @param ficheroInicial fichero ATOM o ZIP que se carga al arrancar si no hay ninguna licitación (demo y e2e)
 */
@ConfigurationProperties("radar.ingesta")
@Validated
public record PropiedadesDeIngesta(
        @NotNull URI feedUrl,
        @NotNull String paquetesUrl,
        @NotBlank String directorioDescargas,
        @NotNull String cron,
        @NotNull ZoneId zona,
        boolean programada,
        @Min(1) int maxPaginasFeed,
        @Min(1) int paginasFeedSinMarca,
        @NotNull Duration tiempoMaximoPeticion,
        @NotEmpty List<String> hostsPermitidos,
        String ficheroInicial) {

    // Las rutas se leen como texto: el conversor de Spring a Path las trataría como recursos y rechaza «../».
    Path carpetaDeDescargas() {
        return Path.of(directorioDescargas);
    }

    Optional<Path> ficheroInicialSiHay() {
        return ficheroInicial == null || ficheroInicial.isBlank()
                ? Optional.empty()
                : Optional.of(Path.of(ficheroInicial));
    }
}
