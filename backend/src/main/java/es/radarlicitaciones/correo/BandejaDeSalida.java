package es.radarlicitaciones.correo;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Encola correos para enviar. */
@Service
public class BandejaDeSalida {

    /** Un correo con su versión en texto y en HTML (los clientes de correo eligen). */
    public record CorreoNuevo(String destinatario, String asunto, String texto, String html) {
        public CorreoNuevo {
            Objects.requireNonNull(destinatario, "destinatario");
            Objects.requireNonNull(asunto, "asunto");
            Objects.requireNonNull(texto, "texto");
            Objects.requireNonNull(html, "html");
        }
    }

    private final JdbcClient jdbc;
    private final Clock reloj;

    BandejaDeSalida(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /**
     * Encola el correo en la transacción en curso: si quien lo encola falla y deshace sus cambios, el correo tampoco se
     * envía.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void encolar(CorreoNuevo correo) {
        var ahora = OffsetDateTime.now(reloj);
        jdbc.sql("""
                        INSERT INTO correo_saliente (destinatario, asunto, texto, html, creado_en, proximo_intento)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(correo.destinatario(), correo.asunto(), correo.texto(), correo.html(), ahora, ahora)
                .update();
    }
}
