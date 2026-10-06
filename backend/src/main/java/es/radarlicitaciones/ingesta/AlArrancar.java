package es.radarlicitaciones.ingesta;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tareas al arrancar.
 *
 * <ul>
 *   <li>Una ingesta que figura «en curso» al arrancar se cortó con la aplicación (un despliegue, una caída): se marca
 *       como fallida para que no bloquee las siguientes y, si era un paquete, se pueda reanudar. Esto supone una sola
 *       instancia de la API, que es como se despliega (ver docs/despliegue.md).
 *   <li>Si se ha configurado un fichero inicial y no hay ninguna licitación, se carga (demo y pruebas e2e).
 * </ul>
 */
@Component
class AlArrancar {

    private static final Logger log = LoggerFactory.getLogger(AlArrancar.class);

    private final JobRepository repositorio;
    private final ServicioDeIngesta servicio;
    private final PropiedadesDeIngesta propiedades;

    AlArrancar(JobRepository repositorio, ServicioDeIngesta servicio, PropiedadesDeIngesta propiedades) {
        this.repositorio = repositorio;
        this.servicio = servicio;
        this.propiedades = propiedades;
    }

    @EventListener(ApplicationReadyEvent.class)
    void alArrancar() {
        for (var ejecucion : repositorio.findRunningJobExecutions(TrabajoDeIngesta.JOB)) {
            log.warn(
                    "La ingesta {} se quedó a medias al parar la aplicación: se marca como fallida", ejecucion.getId());
            var ahora = LocalDateTime.now();
            for (var paso : ejecucion.getStepExecutions()) {
                if (paso.getStatus().isRunning()) {
                    paso.setStatus(BatchStatus.FAILED);
                    paso.setExitStatus(ExitStatus.FAILED);
                    paso.setEndTime(ahora);
                    repositorio.update(paso);
                }
            }
            ejecucion.setStatus(BatchStatus.FAILED);
            ejecucion.setExitStatus(ExitStatus.FAILED.addExitDescription("Interrumpida al parar la aplicación"));
            ejecucion.setEndTime(ahora);
            repositorio.update(ejecucion);
        }

        var inicial = propiedades.ficheroInicialSiHay().orElse(null);
        if (inicial != null && servicio.licitacionesGuardadas() == 0) {
            log.info("No hay licitaciones: se carga el fichero inicial {}", inicial);
            servicio.lanzarEnSegundoPlano(new SolicitudDeIngesta.Fichero(inicial));
        }
    }
}
