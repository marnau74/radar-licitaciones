package es.radarlicitaciones.correo;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envía lo que hay en la bandeja de salida. Si un envío falla se reintenta más tarde, cada vez esperando más; tras
 * {@value #MAX_INTENTOS} intentos se deja de intentar y queda anotado el último error.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED}: aunque hubiera dos instancias, cada correo lo envía una sola.
 */
@Component
@ConditionalOnBooleanProperty("radar.correo.envio-activo")
class EnviadorDeCorreos {

    static final int MAX_INTENTOS = 6;
    private static final int POR_RONDA = 20;
    private static final List<Duration> ESPERAS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12));

    private static final Logger log = LoggerFactory.getLogger(EnviadorDeCorreos.class);

    private final JdbcClient jdbc;
    private final JavaMailSender correo;
    private final PropiedadesDeCorreo propiedades;
    private final Clock reloj;

    EnviadorDeCorreos(JdbcClient jdbc, JavaMailSender correo, PropiedadesDeCorreo propiedades, Clock reloj) {
        this.jdbc = jdbc;
        this.correo = correo;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    private record Pendiente(long id, String destinatario, String asunto, String texto, String html, int intentos) {}

    @Scheduled(fixedDelayString = "${radar.correo.intervalo}")
    @Transactional
    public void enviarPendientes() {
        var ahora = OffsetDateTime.now(reloj);
        var pendientes = jdbc.sql("""
                        SELECT id, destinatario, asunto, texto, html, intentos FROM correo_saliente
                        WHERE enviado_en IS NULL AND intentos < :maximo AND proximo_intento <= :ahora
                        ORDER BY proximo_intento LIMIT :limite
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("maximo", MAX_INTENTOS)
                .param("ahora", ahora)
                .param("limite", POR_RONDA)
                .query(Pendiente.class)
                .list();
        for (var pendiente : pendientes) {
            enviar(pendiente, ahora);
        }
    }

    private void enviar(Pendiente pendiente, OffsetDateTime ahora) {
        try {
            var mensaje = correo.createMimeMessage();
            var ayudante = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            ayudante.setFrom(propiedades.remitente());
            ayudante.setTo(pendiente.destinatario());
            ayudante.setSubject(pendiente.asunto());
            ayudante.setText(pendiente.texto(), pendiente.html());
            correo.send(mensaje);
            jdbc.sql(
                            "UPDATE correo_saliente SET enviado_en = ?, intentos = intentos + 1, ultimo_error = NULL WHERE id = ?")
                    .params(ahora, pendiente.id())
                    .update();
        } catch (MailException | MessagingException e) {
            var intentos = pendiente.intentos() + 1;
            var espera = ESPERAS.get(Math.min(intentos - 1, ESPERAS.size() - 1));
            log.warn(
                    "No se ha podido enviar el correo {} (intento {} de {}): {}",
                    pendiente.id(),
                    intentos,
                    MAX_INTENTOS,
                    e.getMessage());
            jdbc.sql("UPDATE correo_saliente SET intentos = ?, proximo_intento = ?, ultimo_error = ? WHERE id = ?")
                    .params(intentos, ahora.plus(espera), recortar(e.getMessage()), pendiente.id())
                    .update();
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return "Error sin mensaje";
        }
        return texto.length() <= 1000 ? texto : texto.substring(0, 1000);
    }
}
