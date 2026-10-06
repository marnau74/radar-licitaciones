package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.compartido.Conflicto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** La ingesta diaria del feed. Se puede desactivar con {@code radar.ingesta.programada=false} (tests, demo). */
@Component
@ConditionalOnBooleanProperty("radar.ingesta.programada")
class Planificador {

    private static final Logger log = LoggerFactory.getLogger(Planificador.class);

    private final ServicioDeIngesta servicio;

    Planificador(ServicioDeIngesta servicio) {
        this.servicio = servicio;
    }

    @Scheduled(cron = "${radar.ingesta.cron}", zone = "${radar.ingesta.zona}")
    void ingestaDiaria() {
        try {
            servicio.lanzar(new SolicitudDeIngesta.Feed());
        } catch (Conflicto e) {
            log.warn("No se lanza la ingesta diaria: {}", e.getMessage());
        }
    }
}
