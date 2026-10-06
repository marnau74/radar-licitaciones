package es.radarlicitaciones.alertas;

import es.radarlicitaciones.correo.BandejaDeSalida;
import es.radarlicitaciones.ingesta.IngestaCompletada;
import es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Tras cada ingesta, busca lo nuevo de cada alerta activa, lo guarda como avisos y encola un correo por usuario.
 *
 * <p>Corre en segundo plano y en su propia transacción ({@link ApplicationModuleListener}); si falla, el evento queda
 * pendiente en el registro de Spring Modulith y se vuelve a entregar al arrancar. Repetirlo no duplica nada: la
 * restricción única de {@code aviso} descarta lo ya avisado y solo se manda correo de lo nuevo.
 */
@Component
class GeneradorDeAvisos {

    /** Como mucho, avisos por alerta y por ingesta: más sería una alerta mal afinada. */
    static final int MAX_POR_ALERTA = 100;

    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private static final Logger log = LoggerFactory.getLogger(GeneradorDeAvisos.class);

    private final AlertaRepositorio alertas;
    private final BuscadorDeLicitaciones buscador;
    private final JdbcClient jdbc;
    private final BandejaDeSalida bandeja;
    private final CorreoDeAvisos correo;
    private final Clock reloj;

    GeneradorDeAvisos(
            AlertaRepositorio alertas,
            BuscadorDeLicitaciones buscador,
            JdbcClient jdbc,
            BandejaDeSalida bandeja,
            CorreoDeAvisos correo,
            Clock reloj) {
        this.alertas = alertas;
        this.buscador = buscador;
        this.jdbc = jdbc;
        this.bandeja = bandeja;
        this.correo = correo;
        this.reloj = reloj;
    }

    @ApplicationModuleListener
    void alCompletarIngesta(IngestaCompletada ingesta) {
        var ahora = OffsetDateTime.now(reloj);
        var hoy = LocalDate.now(reloj.withZone(ZONA));
        // Por usuario, sus alertas con lo nuevo de cada una (en orden de creación de las alertas).
        var porUsuario = new LinkedHashMap<String, List<CorreoDeAvisos.Seccion>>();
        var correoDeUsuario = new LinkedHashMap<String, String>();
        var total = 0;

        for (var alerta : alertas.findByActivaTrueOrderById()) {
            var candidatas = buscador.novedades(
                    alerta.filtro(),
                    ingesta.desde(),
                    ingesta.hasta(),
                    alerta.publicadasDesde(hoy, ZONA),
                    MAX_POR_ALERTA);
            var nuevas = guardarAvisos(alerta, candidatas, ingesta.ingesta(), ahora);
            if (nuevas.isEmpty()) {
                continue;
            }
            total += nuevas.size();
            if (alerta.isPorCorreo()) {
                porUsuario
                        .computeIfAbsent(alerta.getUsuarioId(), u -> new ArrayList<>())
                        .add(new CorreoDeAvisos.Seccion(alerta.getNombre(), buscador.porIds(nuevas)));
                correoDeUsuario.put(alerta.getUsuarioId(), alerta.getCorreo());
            }
        }

        porUsuario.forEach(
                (usuario, secciones) -> bandeja.encolar(correo.componer(correoDeUsuario.get(usuario), secciones)));
        log.info("Ingesta {}: {} avisos nuevos, {} correos encolados", ingesta.ingesta(), total, porUsuario.size());
    }

    /** Guarda los avisos y devuelve las licitaciones de las que no se había avisado antes a esta alerta. */
    private List<Long> guardarAvisos(Alerta alerta, List<Long> licitaciones, long ingesta, OffsetDateTime ahora) {
        if (licitaciones.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        INSERT INTO aviso (alerta_id, licitacion_id, ingesta_id, creado_en)
                        SELECT :alerta, id, :ingesta, :ahora FROM unnest(CAST(:licitaciones AS bigint[])) AS id
                        ON CONFLICT (alerta_id, licitacion_id) DO NOTHING
                        RETURNING licitacion_id
                        """)
                .param("alerta", alerta.getId())
                .param("ingesta", ingesta)
                .param("ahora", ahora)
                .param("licitaciones", licitaciones.toArray(Long[]::new))
                .query(Long.class)
                .list();
    }
}
