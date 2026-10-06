package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.compartido.Conflicto;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Lanza las ingestas, de una en una. Dos ingestas a la vez no romperían los datos (cada versión solo pisa a otra más
 * antigua), pero sí la marca del feed y las cuentas de los avisos.
 */
@Service
class ServicioDeIngesta {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeIngesta.class);

    private final JobOperator operador;
    private final JobRepository repositorio;
    private final Job trabajo;
    private final TaskExecutor ejecutor;
    private final JdbcClient jdbc;
    private final Clock reloj;
    private final ReentrantLock cerrojo = new ReentrantLock();

    ServicioDeIngesta(
            JobOperator operador,
            JobRepository repositorio,
            Job ingestaLicitaciones,
            @Qualifier("applicationTaskExecutor") TaskExecutor ejecutor,
            JdbcClient jdbc,
            Clock reloj) {
        this.operador = operador;
        this.repositorio = repositorio;
        this.trabajo = ingestaLicitaciones;
        this.ejecutor = ejecutor;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Lanza la ingesta y espera a que termine. */
    JobExecution lanzar(SolicitudDeIngesta solicitud) {
        cerrojo.lock();
        try {
            comprobarQueNoHayOtra();
            return operador.start(trabajo, parametros(solicitud));
        } catch (JobInstanceAlreadyCompleteException e) {
            throw new Conflicto("Esa ingesta ya se hizo y terminó bien: "
                    + FinDeIngesta.descripcion(solicitud.origen(), solicitud.valor()));
        } catch (Conflicto e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se ha podido lanzar la ingesta", e);
        } finally {
            cerrojo.unlock();
        }
    }

    /** Comprueba que se puede lanzar y la lanza en segundo plano. */
    void lanzarEnSegundoPlano(SolicitudDeIngesta solicitud) {
        comprobarQueNoHayOtra();
        ejecutor.execute(() -> {
            try {
                lanzar(solicitud);
            } catch (RuntimeException e) {
                log.error("La ingesta {} no ha podido empezar", solicitud, e);
            }
        });
    }

    boolean hayUnaEnCurso() {
        return !repositorio.findRunningJobExecutions(TrabajoDeIngesta.JOB).isEmpty() || cerrojo.isLocked();
    }

    private void comprobarQueNoHayOtra() {
        if (!repositorio.findRunningJobExecutions(TrabajoDeIngesta.JOB).isEmpty()) {
            throw new Conflicto("Ya hay una ingesta en curso");
        }
    }

    /**
     * El feed y los ficheros llevan un identificador único: cada vez es una ingesta nueva. Un paquete se identifica solo
     * por su periodo: si falla, volver a pedirlo continúa la ingesta fallida desde donde se quedó (y si ya terminó bien,
     * se rechaza).
     */
    private JobParameters parametros(SolicitudDeIngesta solicitud) {
        var parametros = new JobParametersBuilder().addString(TrabajoDeIngesta.PARAMETRO_ORIGEN, solicitud.origen());
        if (solicitud.valor() != null) {
            parametros.addString(TrabajoDeIngesta.PARAMETRO_VALOR, solicitud.valor());
        }
        if (!(solicitud instanceof SolicitudDeIngesta.Paquete)) {
            parametros.addString(
                    TrabajoDeIngesta.PARAMETRO_EJECUCION, UUID.randomUUID().toString());
        }
        parametros.addString(
                TrabajoDeIngesta.PARAMETRO_LANZADA, OffsetDateTime.now(reloj).toString(), false);
        return parametros.toJobParameters();
    }

    List<ResumenDeIngesta> ultimas(int cuantas) {
        return jdbc.sql("""
                        SELECT id, origen, descripcion, inicio, fin, estado, ficheros, leidas, nuevas, actualizadas,
                               sin_cambios, anuladas, ilegibles, error
                        FROM ingesta ORDER BY fin DESC LIMIT ?
                        """)
                .param(cuantas)
                .query((rs, fila) -> new ResumenDeIngesta(
                        rs.getLong("id"),
                        rs.getString("origen"),
                        rs.getString("descripcion"),
                        rs.getObject("inicio", OffsetDateTime.class),
                        rs.getObject("fin", OffsetDateTime.class),
                        rs.getString("estado"),
                        rs.getInt("ficheros"),
                        rs.getInt("leidas"),
                        rs.getInt("nuevas"),
                        rs.getInt("actualizadas"),
                        rs.getInt("sin_cambios"),
                        rs.getInt("anuladas"),
                        rs.getInt("ilegibles"),
                        rs.getString("error")))
                .list();
    }

    long licitacionesGuardadas() {
        return jdbc.sql("SELECT count(*) FROM licitacion").query(Long.class).single();
    }

    /** El resumen de una ingesta terminada. */
    record ResumenDeIngesta(
            long id,
            String origen,
            String descripcion,
            OffsetDateTime inicio,
            OffsetDateTime fin,
            String estado,
            int ficheros,
            int leidas,
            int nuevas,
            int actualizadas,
            int sinCambios,
            int anuladas,
            int ilegibles,
            String error) {

        /** Lo que se enseña sin iniciar sesión: sin el detalle del error. */
        ResumenDeIngesta publico() {
            return new ResumenDeIngesta(
                    id,
                    origen,
                    descripcion,
                    inicio,
                    fin,
                    estado,
                    ficheros,
                    leidas,
                    nuevas,
                    actualizadas,
                    sinCambios,
                    anuladas,
                    ilegibles,
                    null);
        }
    }
}
