package es.radarlicitaciones.ingesta.fuentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.radarlicitaciones.ingesta.codice.FicheroNoValido;
import es.radarlicitaciones.ingesta.fuentes.Fuente.FicheroLeido;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Hasta dónde retrocede el feed vivo. No se descarga nada: solo se comprueba qué página pediría. */
class FeedVivoTest {

    private static final URI PRIMERA = URI.create("https://contrataciondelsectorpublico.gob.es/feed.atom");
    private static final String ANTERIOR = "https://contrataciondelestado.es/feed_20261005.atom";
    private static final OffsetDateTime MARCA = OffsetDateTime.parse("2026-10-05T12:00:00+02:00");

    private final Descargador descargador = new Descargador(
            List.of("contrataciondelsectorpublico.gob.es", "contrataciondelestado.es"), Duration.ofSeconds(5));

    private static FicheroLeido leido(String enlace, String masAntiguo) {
        return new FicheroLeido(
                Optional.ofNullable(enlace), masAntiguo == null ? null : OffsetDateTime.parse(masAntiguo));
    }

    @Test
    void empiezaPorLaPrimeraPagina() {
        var feed = new FeedVivo(descargador, PRIMERA, MARCA, 10);
        assertThat(feed.fichero(0, null))
                .get()
                .extracting(Fuente.FicheroDeFeed::nombre)
                .isEqualTo(PRIMERA.toString());
    }

    @Test
    void retrocedeMientrasLaPaginaEsPosteriorALaMarca() {
        var feed = new FeedVivo(descargador, PRIMERA, MARCA, 10);
        assertThat(feed.fichero(1, leido(ANTERIOR, "2026-10-05T15:00:00+02:00")))
                .get()
                .extracting(Fuente.FicheroDeFeed::nombre)
                .isEqualTo(ANTERIOR);
    }

    @Test
    void separaAlPasarLaMarca() {
        var feed = new FeedVivo(descargador, PRIMERA, MARCA, 10);
        assertThat(feed.fichero(1, leido(ANTERIOR, "2026-10-05T11:59:59+02:00")))
                .isEmpty();
    }

    @Test
    void separaSiNoHayMasPaginasOSeLlegaAlTope() {
        assertThat(new FeedVivo(descargador, PRIMERA, MARCA, 10).fichero(1, leido(null, "2026-10-05T15:00:00+02:00")))
                .isEmpty();
        assertThat(new FeedVivo(descargador, PRIMERA, MARCA, 3)
                        .fichero(3, leido(ANTERIOR, "2026-10-05T15:00:00+02:00")))
                .isEmpty();
    }

    @Test
    void sinMarcaLeeSoloLasPaginasIndicadas() {
        var feed = new FeedVivo(descargador, PRIMERA, null, 2);
        assertThat(feed.fichero(1, leido(ANTERIOR, "2020-01-01T00:00:00+01:00")))
                .isPresent();
        assertThat(feed.fichero(2, leido(ANTERIOR, "2020-01-01T00:00:00+01:00")))
                .isEmpty();
        assertThat(feed.reanudable()).isFalse();
    }

    @Test
    void soloDescargaPorHttpsDeLosServidoresPermitidos() {
        assertThatThrownBy(() -> descargador.comprobarPermitido(URI.create("http://contrataciondelestado.es/a.atom")))
                .isInstanceOf(FicheroNoValido.class);
        assertThatThrownBy(() -> descargador.comprobarPermitido(URI.create("https://otro.example/a.atom")))
                .isInstanceOf(FicheroNoValido.class);
        assertThatThrownBy(() -> descargador.comprobarPermitido(URI.create("https://169.254.169.254/latest/meta-data")))
                .isInstanceOf(FicheroNoValido.class);
        descargador.comprobarPermitido(URI.create("https://CONTRATACIONDELESTADO.ES/a.atom"));
    }
}
