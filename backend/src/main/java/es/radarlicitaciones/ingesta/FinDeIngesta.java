package es.radarlicitaciones.ingesta;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Al empezar, anota la hora de la base de datos; al terminar, guarda el resumen de la ingesta y, si ha ido bien, avanza
 * la marca del feed y publica {@link IngestaCompletada}. Las tres cosas en una transacción: el evento queda en el
 * registro de publicaciones de Spring Modulith y se entrega aunque la aplicación se pare justo después.
 *
 * <p>Las horas son siempre las de la base de datos, las mismas con las que se rellena {@code ingerida_en}: así no hay
 * desfases entre relojes.
 */
@Component
class FinDeIngesta implements JobExecutionListener {

    static final String INICIO = "ingesta.inicio";

    private static final Logger log = LoggerFactory.getLogger(FinDeIngesta.class);
    private static final int MAX_ERROR = 2000;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;
    private final MarcaDelFeed marca;
    private final ApplicationEventPublisher eventos;

    FinDeIngesta(
            JdbcClient jdbc, TransactionTemplate transaccion, MarcaDelFeed marca, ApplicationEventPublisher eventos) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.marca = marca;
        this.eventos = eventos;
    }

    @Override
    public void beforeJob(JobExecution ejecucion) {
        ejecucion.getExecutionContext().putString(INICIO, ahoraEnBd().toString());
    }

    @Override
    public void afterJob(JobExecution ejecucion) {
        var parametros = ejecucion.getJobParameters();
        var origen = parametros.getString(TrabajoDeIngesta.PARAMETRO_ORIGEN);
        var valor = parametros.getString(TrabajoDeIngesta.PARAMETRO_VALOR);
        var inicio = OffsetDateTime.parse(
                ejecucion.getExecutionContext().getString(INICIO, ahoraEnBd().toString()));
        var cuentas = Cuentas.de(ejecucion);
        var completada = ejecucion.getStatus() == BatchStatus.COMPLETED;
        var error = ejecucion.getAllFailureExceptions().stream()
                .map(e -> e.getClass().getSimpleName() + ": " + e.getMessage())
                .collect(Collectors.joining("\n"));

        transaccion.executeWithoutResult(estado -> {
            var fin = ahoraEnBd();
            var anteriorCompletada = jdbc.sql("SELECT max(fin) FROM ingesta WHERE estado = 'COMPLETED'")
                    .query(OffsetDateTime.class)
                    .optional()
                    .orElse(null);
            jdbc.sql("""
                            INSERT INTO ingesta (id, origen, descripcion, inicio, fin, estado, ficheros, leidas, nuevas,
                                                 actualizadas, sin_cambios, anuladas, ilegibles, error)
                            VALUES (:id, :origen, :descripcion, :inicio, :fin, :estado, :ficheros, :leidas, :nuevas,
                                    :actualizadas, :sinCambios, :anuladas, :ilegibles, :error)
                            ON CONFLICT (id) DO UPDATE SET fin = EXCLUDED.fin, estado = EXCLUDED.estado,
                                ficheros = EXCLUDED.ficheros, leidas = EXCLUDED.leidas, nuevas = EXCLUDED.nuevas,
                                actualizadas = EXCLUDED.actualizadas, sin_cambios = EXCLUDED.sin_cambios,
                                anuladas = EXCLUDED.anuladas, ilegibles = EXCLUDED.ilegibles, error = EXCLUDED.error
                            """)
                    .param("id", ejecucion.getId())
                    .param("origen", origen)
                    .param("descripcion", descripcion(origen, valor))
                    .param("inicio", inicio)
                    .param("fin", fin)
                    .param("estado", ejecucion.getStatus().name())
                    .param("ficheros", cuentas.ficheros())
                    .param("leidas", cuentas.leidas())
                    .param("nuevas", cuentas.nuevas())
                    .param("actualizadas", cuentas.actualizadas())
                    .param("sinCambios", cuentas.sinCambios())
                    .param("anuladas", cuentas.anuladas())
                    .param("ilegibles", cuentas.ilegibles())
                    .param("error", error.isEmpty() ? null : recortar(error))
                    .update();
            if (!completada) {
                return;
            }
            if ("feed".equals(origen) && cuentas.masReciente() != null) {
                marca.avanzar(cuentas.masReciente());
            }
            var desde = anteriorCompletada == null ? inicio : anteriorCompletada;
            eventos.publishEvent(new IngestaCompletada(
                    ejecucion.getId(),
                    origen,
                    desde.toInstant(),
                    fin.toInstant(),
                    cuentas.nuevas(),
                    cuentas.actualizadas()));
        });

        log.info(
                "Ingesta {} ({}) terminada: {}. Ficheros {}, leídas {}, nuevas {}, actualizadas {}, sin cambios {},"
                        + " anulaciones {}, ilegibles {}",
                ejecucion.getId(),
                descripcion(origen, valor),
                ejecucion.getStatus(),
                cuentas.ficheros(),
                cuentas.leidas(),
                cuentas.nuevas(),
                cuentas.actualizadas(),
                cuentas.sinCambios(),
                cuentas.anuladas(),
                cuentas.ilegibles());
    }

    static String descripcion(String origen, String valor) {
        return switch (origen) {
            case "feed" -> "Feed diario";
            case "paquete" -> "Paquete " + valor;
            case "fichero" -> "Fichero " + Path.of(valor).getFileName();
            default -> origen;
        };
    }

    private OffsetDateTime ahoraEnBd() {
        return jdbc.sql("SELECT now()").query(OffsetDateTime.class).single();
    }

    private static String recortar(String texto) {
        return texto.length() <= MAX_ERROR ? texto : texto.substring(0, MAX_ERROR) + "…";
    }

    /** Las cuentas de los pasos de la ejecución (solo hay uno, pero no cuesta nada sumar). */
    record Cuentas(
            int ficheros,
            long leidas,
            int nuevas,
            int actualizadas,
            int sinCambios,
            int anuladas,
            int ilegibles,
            OffsetDateTime masReciente) {

        static Cuentas de(JobExecution ejecucion) {
            int ficheros = 0, nuevas = 0, actualizadas = 0, sinCambios = 0, anuladas = 0, ilegibles = 0;
            long leidas = 0;
            OffsetDateTime masReciente = null;
            for (var paso : ejecucion.getStepExecutions()) {
                var contexto = paso.getExecutionContext();
                ficheros += contexto.getInt(LectorDeFuente.FICHEROS_LEIDOS, 0);
                leidas += paso.getReadCount();
                nuevas += contexto.getInt(EscritorDeElementos.NUEVAS, 0);
                actualizadas += contexto.getInt(EscritorDeElementos.ACTUALIZADAS, 0);
                sinCambios += contexto.getInt(EscritorDeElementos.SIN_CAMBIOS, 0);
                anuladas += contexto.getInt(EscritorDeElementos.ANULADAS, 0);
                ilegibles += contexto.getInt(EscritorDeElementos.ILEGIBLES, 0);
                if (contexto.containsKey(LectorDeFuente.MAS_RECIENTE)) {
                    var fecha = OffsetDateTime.parse(contexto.getString(LectorDeFuente.MAS_RECIENTE));
                    if (masReciente == null || fecha.isAfter(masReciente)) {
                        masReciente = fecha;
                    }
                }
            }
            return new Cuentas(ficheros, leidas, nuevas, actualizadas, sinCambios, anuladas, ilegibles, masReciente);
        }
    }
}
