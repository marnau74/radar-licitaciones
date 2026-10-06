package es.radarlicitaciones.licitaciones.internal;

import es.radarlicitaciones.catalogos.Catalogo;
import es.radarlicitaciones.catalogos.Catalogos;
import es.radarlicitaciones.catalogos.Codigo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cifras del panel. Son agregados sobre toda la tabla, así que se calculan como mucho una vez cada cinco minutos: los
 * datos solo cambian con cada ingesta y no merece la pena recalcularlos en cada visita.
 */
@Repository
@Transactional(readOnly = true)
public class EstadisticasJdbc {

    static final Duration VIGENCIA = Duration.ofMinutes(5);

    /** En plazo: publicada, no anulada y con el plazo sin cerrar (o sin plazo conocido). */
    private static final String EN_PLAZO =
            "estado = 'PUB' AND NOT anulada AND (plazo_presentacion IS NULL OR plazo_presentacion > :ahora)";

    public record Estadisticas(
            Instant calculadasEn,
            long total,
            long enPlazo,
            BigDecimal importeEnPlazo,
            long publicadasUltimos30Dias,
            List<Grupo> enPlazoPorTipo,
            List<Grupo> enPlazoPorComunidad,
            List<Mes> publicadasPorMes) {}

    public record Grupo(Codigo codigo, long licitaciones, BigDecimal importe) {}

    public record Mes(YearMonth mes, long licitaciones, BigDecimal importe) {}

    private record EnCache(Estadisticas estadisticas, Instant hasta) {}

    private final JdbcClient jdbc;
    private final Catalogos catalogos;
    private final Clock reloj;
    private final AtomicReference<EnCache> cache = new AtomicReference<>();

    EstadisticasJdbc(JdbcClient jdbc, Catalogos catalogos, Clock reloj) {
        this.jdbc = jdbc;
        this.catalogos = catalogos;
        this.reloj = reloj;
    }

    public Estadisticas calcular() {
        var ahora = reloj.instant();
        var guardadas = cache.get();
        if (guardadas != null && ahora.isBefore(guardadas.hasta())) {
            return guardadas.estadisticas();
        }
        var nuevas = calcularAhora(ahora);
        cache.set(new EnCache(nuevas, ahora.plus(VIGENCIA)));
        return nuevas;
    }

    private Estadisticas calcularAhora(Instant ahora) {
        var hoy = LocalDate.ofInstant(ahora, BuscadorJdbc.ZONA);
        var momento = OffsetDateTime.ofInstant(ahora, BuscadorJdbc.ZONA);
        var resumen = jdbc.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE %1$s) AS en_plazo,
                               count(*) FILTER (WHERE fecha_publicacion > :hace30 AND NOT anulada) AS ultimos_30
                        FROM licitacion
                        """.formatted(EN_PLAZO))
                .param("hace30", hoy.minusDays(30))
                .param("ahora", momento)
                .query((rs, fila) -> new long[] {rs.getLong("total"), rs.getLong("en_plazo"), rs.getLong("ultimos_30")})
                .single();
        var importeEnPlazo = jdbc.sql("SELECT coalesce(sum(importe_sin_iva), 0) FROM licitacion WHERE " + EN_PLAZO)
                .param("ahora", momento)
                .query(BigDecimal.class)
                .single();

        var porTipo = jdbc.sql("""
                        SELECT tipo_contrato AS codigo, count(*) AS n, coalesce(sum(importe_sin_iva), 0) AS importe
                        FROM licitacion WHERE tipo_contrato IS NOT NULL AND %s
                        GROUP BY tipo_contrato ORDER BY n DESC
                        """.formatted(EN_PLAZO))
                .param("ahora", momento)
                .query((rs, fila) -> new Grupo(
                        catalogos
                                .codigo(Catalogo.TIPO_CONTRATO, rs.getString("codigo"))
                                .orElseThrow(),
                        rs.getLong("n"),
                        rs.getBigDecimal("importe")))
                .list();
        var porComunidad = jdbc.sql("""
                        SELECT left(nuts, 4) AS codigo, count(*) AS n, coalesce(sum(importe_sin_iva), 0) AS importe
                        FROM licitacion WHERE length(nuts) >= 4 AND %s
                        GROUP BY left(nuts, 4) ORDER BY n DESC
                        """.formatted(EN_PLAZO))
                .param("ahora", momento)
                .query((rs, fila) -> new Grupo(
                        catalogos.codigo(Catalogo.NUTS, rs.getString("codigo")).orElseThrow(),
                        rs.getLong("n"),
                        rs.getBigDecimal("importe")))
                .list();
        var desde = YearMonth.from(hoy).minusMonths(11);
        var conDatos = jdbc
                .sql("""
                        SELECT date_trunc('month', fecha_publicacion)::date AS mes, count(*) AS n,
                               coalesce(sum(importe_sin_iva), 0) AS importe
                        FROM licitacion WHERE fecha_publicacion >= :desde AND NOT anulada
                        GROUP BY 1 ORDER BY 1
                        """)
                .param("desde", desde.atDay(1))
                .query((rs, fila) -> new Mes(
                        YearMonth.from(rs.getObject("mes", LocalDate.class)),
                        rs.getLong("n"),
                        rs.getBigDecimal("importe")))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Mes::mes, m -> m));
        // Los meses sin publicaciones también se enseñan (a cero): si no, la gráfica engaña.
        var porMes = java.util.stream.IntStream.range(0, 12)
                .mapToObj(desde::plusMonths)
                .map(mes -> conDatos.getOrDefault(mes, new Mes(mes, 0, BigDecimal.ZERO)))
                .toList();

        return new Estadisticas(
                ahora, resumen[0], resumen[1], importeEnPlazo, resumen[2], porTipo, porComunidad, porMes);
    }
}
