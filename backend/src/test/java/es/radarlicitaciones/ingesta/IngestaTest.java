package es.radarlicitaciones.ingesta;

import static es.radarlicitaciones.Muestra.OBRAS_EN_PLAZO_CORUNA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import es.radarlicitaciones.Muestra;
import es.radarlicitaciones.PruebaDeIntegracion;
import es.radarlicitaciones.compartido.Conflicto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * La ingesta de punta a punta: el job de Spring Batch lee la muestra, guarda, publica el evento y el módulo de alertas
 * genera los avisos y el correo, que llega de verdad a Mailpit.
 */
class IngestaTest extends PruebaDeIntegracion {

    @Autowired
    ServicioDeIngesta servicio;

    @Value("${pruebas.mailpit-url}")
    String mailpit;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void vaciarMailpit() throws Exception {
        http.send(
                HttpRequest.newBuilder(URI.create(mailpit + "/api/v1/messages"))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private void crearAlerta(String usuario, String correo, String tipos, String nuts, boolean porCorreo) {
        jdbc.sql("""
                        INSERT INTO alerta (usuario_id, correo, nombre, tipos_contrato, nuts, por_correo, activa,
                                            creada_en, actualizada_en)
                        VALUES (?, ?, 'Obras en A Coruña', CAST(? AS text[]), CAST(? AS text[]), ?, true,
                                '2026-10-01T09:00:00+02:00', '2026-10-01T09:00:00+02:00')
                        """).params(usuario, correo, tipos, nuts, porCorreo).update();
    }

    private long avisos() {
        return jdbc.sql("SELECT count(*) FROM aviso").query(Long.class).single();
    }

    private long eventosPendientes() {
        return jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NULL")
                .query(Long.class)
                .single();
    }

    private List<String> asuntosEnMailpit() throws Exception {
        var respuesta = http.send(
                HttpRequest.newBuilder(URI.create(mailpit + "/api/v1/messages")).build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonPath.read(respuesta.body(), "$.messages[*].Subject");
    }

    @Test
    void ingiereLaMuestraYGuardaElResumen() {
        var ejecucion = servicio.lanzar(new SolicitudDeIngesta.Fichero(Muestra.ruta()));

        assertThat(ejecucion.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var resumen = servicio.ultimas(1).getFirst();
        assertThat(resumen.estado()).isEqualTo("COMPLETED");
        assertThat(resumen.descripcion()).isEqualTo("Fichero muestra.atom");
        assertThat(resumen.ficheros()).isEqualTo(1);
        assertThat(resumen.leidas()).isEqualTo(12);
        assertThat(resumen.nuevas()).isEqualTo(10);
        assertThat(resumen.anuladas()).isEqualTo(2);
        assertThat(resumen.ilegibles()).isZero();

        var otraVez = servicio.lanzar(new SolicitudDeIngesta.Fichero(Muestra.ruta()));
        assertThat(otraVez.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var segunda = servicio.ultimas(1).getFirst();
        assertThat(segunda.nuevas()).isZero();
        assertThat(segunda.sinCambios()).isEqualTo(10);
    }

    @Test
    void avisaUnaSolaVezDeLoNuevoQueEncajaConCadaAlerta() throws Exception {
        crearAlerta("usuario-1", "ana@demo.example", "{3}", "{ES111}", true);
        crearAlerta("usuario-2", "luis@demo.example", "{2}", "{ES7}", true); // no encaja con nada de la muestra

        servicio.lanzar(new SolicitudDeIngesta.Fichero(Muestra.ruta()));
        await().atMost(Duration.ofSeconds(20)).until(() -> eventosPendientes() == 0 && avisos() > 0);

        // De las dos obras de A Coruña, solo una está en plazo: la otra ya está adjudicada.
        assertThat(jdbc.sql("SELECT licitacion_id FROM aviso").query(Long.class).list())
                .containsExactly(OBRAS_EN_PLAZO_CORUNA);
        await().atMost(Duration.ofSeconds(20)).until(() -> asuntosEnMailpit().size() == 1);
        assertThat(asuntosEnMailpit()).containsExactly("1 licitación nueva para tus alertas");
        assertThat(jdbc.sql("SELECT count(*) FROM correo_saliente WHERE enviado_en IS NOT NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);

        // La misma ingesta otra vez: ni avisos repetidos ni correos.
        servicio.lanzar(new SolicitudDeIngesta.Fichero(Muestra.ruta()));
        await().atMost(Duration.ofSeconds(20)).until(() -> eventosPendientes() == 0);
        assertThat(avisos()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM correo_saliente")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void sinCorreoLosAvisosQuedanSoloEnLaWeb() {
        crearAlerta("usuario-1", "ana@demo.example", "{3}", "{ES111}", false);

        servicio.lanzar(new SolicitudDeIngesta.Fichero(Muestra.ruta()));
        await().atMost(Duration.ofSeconds(20)).until(() -> eventosPendientes() == 0 && avisos() > 0);

        assertThat(jdbc.sql("SELECT count(*) FROM correo_saliente")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void unPaqueteMalPedidoSeRechazaSinLanzarNada() {
        assertThatThrownBy(() -> new SolicitudDeIngesta.Paquete("2026-09")).hasMessageContaining("AAAAMM");
        assertThatThrownBy(() -> new SolicitudDeIngesta.Paquete("2009")).hasMessageContaining("2012");
        assertThat(servicio.ultimas(10)).isEmpty();
    }

    @Test
    void noHayDosIngestasALaVez() throws Exception {
        var ejecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        try (ejecutor) {
            var primera = ejecutor.submit(() -> servicio.lanzar(new SolicitudDeIngesta.Fichero(Muestra.ruta())));
            var segunda = ejecutor.submit(() -> servicio.lanzar(new SolicitudDeIngesta.Fichero(Muestra.ruta())));
            var resultados = new java.util.ArrayList<Object>();
            for (var tarea : List.of(primera, segunda)) {
                try {
                    resultados.add(tarea.get().getStatus());
                } catch (java.util.concurrent.ExecutionException e) {
                    resultados.add(e.getCause().getClass());
                }
            }
            // O una espera a la otra (el cerrojo) y las dos terminan, o la segunda se rechaza: nunca a la vez.
            assertThat(resultados).allMatch(r -> r == BatchStatus.COMPLETED || r == Conflicto.class);
            assertThat(jdbc.sql("SELECT count(*) FROM licitacion")
                            .query(Long.class)
                            .single())
                    .isEqualTo(10);
        }
    }
}
