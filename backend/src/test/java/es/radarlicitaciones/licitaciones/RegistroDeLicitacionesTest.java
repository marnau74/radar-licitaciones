package es.radarlicitaciones.licitaciones;

import static es.radarlicitaciones.Muestra.ANULADA;
import static es.radarlicitaciones.Muestra.SEGUROS_DAGANZO;
import static org.assertj.core.api.Assertions.assertThat;

import es.radarlicitaciones.Muestra;
import es.radarlicitaciones.PruebaDeIntegracion;
import es.radarlicitaciones.licitaciones.RegistroDeLicitaciones.ResultadoDelGuardado;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class RegistroDeLicitacionesTest extends PruebaDeIntegracion {

    @Autowired
    RegistroDeLicitaciones registro;

    @Autowired
    TransactionTemplate transaccion;

    private ResultadoDelGuardado guardar(List<LicitacionLeida> versiones, List<AnulacionLeida> anulaciones) {
        return transaccion.execute(estado -> registro.guardar(versiones, anulaciones));
    }

    private String titulo(long id) {
        return jdbc.sql("SELECT titulo FROM licitacion WHERE id = ?")
                .param(id)
                .query(String.class)
                .single();
    }

    private boolean anulada(long id) {
        return jdbc.sql("SELECT anulada FROM licitacion WHERE id = ?")
                .param(id)
                .query(Boolean.class)
                .single();
    }

    @Test
    void guardaLaMuestraYVolverAGuardarlaNoCambiaNada() {
        var primera = guardar(Muestra.licitaciones(), Muestra.anulaciones());
        assertThat(primera).isEqualTo(new ResultadoDelGuardado(10, 0, 0, 2));

        var segunda = guardar(Muestra.licitaciones(), Muestra.anulaciones());
        assertThat(segunda).isEqualTo(new ResultadoDelGuardado(0, 0, 10, 2));

        assertThat(jdbc.sql("SELECT count(*) FROM licitacion").query(Long.class).single())
                .isEqualTo(10);
        assertThat(jdbc.sql("SELECT count(*) FROM lote WHERE licitacion_id = ?")
                        .param(SEGUROS_DAGANZO)
                        .query(Long.class)
                        .single())
                .isEqualTo(5);
        assertThat(jdbc.sql("SELECT count(*) FROM resultado WHERE licitacion_id = ?")
                        .param(SEGUROS_DAGANZO)
                        .query(Long.class)
                        .single())
                .isEqualTo(5);
    }

    @Test
    void guardaLosPrefijosParaFiltrarPorCpvYNuts() {
        guardar(Muestra.licitaciones(), List.of());
        var fila = jdbc.sql("SELECT cpv_prefijos, nuts_prefijos FROM licitacion WHERE id = ?")
                .param(SEGUROS_DAGANZO)
                .query((rs, n) -> List.of(List.of((String[]) rs.getArray(1).getArray()), List.of((String[])
                        rs.getArray(2).getArray())))
                .single();
        assertThat(fila.get(0)).contains("66", "665", "6651", "66510000", "6651210", "66514110");
        assertThat(fila.get(1)).containsExactly("ES", "ES3", "ES30", "ES300");
    }

    @Test
    void marcaComoAnuladaLaQueTieneUnaAnulacionPosterior() {
        guardar(Muestra.licitaciones(), Muestra.anulaciones());
        assertThat(anulada(ANULADA)).isTrue();
        assertThat(anulada(SEGUROS_DAGANZO)).isFalse();
    }

    @Test
    void unaVersionAnteriorNoPisaLaGuardadaYUnaPosteriorSi() {
        var original = Muestra.licitacion(SEGUROS_DAGANZO);
        guardar(List.of(original), List.of());

        var antigua = Muestra.otraVersion(
                original,
                original.id(),
                "Versión antigua",
                List.of(),
                original.actualizadaEn().minusDays(1));
        assertThat(guardar(List.of(antigua), List.of())).isEqualTo(new ResultadoDelGuardado(0, 0, 1, 0));
        assertThat(titulo(SEGUROS_DAGANZO)).isEqualTo(original.titulo());

        var nueva = Muestra.otraVersion(
                original,
                original.id(),
                "Versión nueva",
                original.lotes().subList(0, 1),
                original.actualizadaEn().plusHours(1));
        assertThat(guardar(List.of(nueva), List.of())).isEqualTo(new ResultadoDelGuardado(0, 1, 0, 0));
        assertThat(titulo(SEGUROS_DAGANZO)).isEqualTo("Versión nueva");
        assertThat(jdbc.sql("SELECT count(*) FROM lote WHERE licitacion_id = ?")
                        .param(SEGUROS_DAGANZO)
                        .query(Long.class)
                        .single())
                .as("los lotes se rehacen con la versión nueva")
                .isEqualTo(1);
    }

    @Test
    void enUnMismoLoteSeQuedaLaVersionMasReciente() {
        var original = Muestra.licitacion(SEGUROS_DAGANZO);
        var nueva = Muestra.otraVersion(
                original,
                original.id(),
                "Versión nueva",
                original.lotes(),
                original.actualizadaEn().plusHours(1));
        assertThat(guardar(List.of(nueva, original), List.of())).isEqualTo(new ResultadoDelGuardado(1, 0, 1, 0));
        assertThat(titulo(SEGUROS_DAGANZO)).isEqualTo("Versión nueva");
    }

    @Test
    void elOrdenEntreAnulacionYVersionesNoImporta() {
        var base = Muestra.licitacion(SEGUROS_DAGANZO);
        var cuando = OffsetDateTime.parse("2026-10-01T12:00:00+02:00");
        guardar(List.of(), List.of(new AnulacionLeida(1, cuando)));

        guardar(
                List.of(Muestra.otraVersion(base, 1, "Anterior a la anulación", List.of(), cuando.minusHours(1))),
                List.of());
        assertThat(anulada(1))
                .as("la anulación llegó antes, pero es posterior a esta versión")
                .isTrue();

        guardar(List.of(Muestra.otraVersion(base, 1, "Republicada", List.of(), cuando.plusHours(1))), List.of());
        assertThat(anulada(1))
                .as("una versión posterior a la anulación la deja sin anular")
                .isFalse();
    }

    @Test
    void unOrganoSeGuardaUnaSolaVez() {
        var base = Muestra.licitacion(SEGUROS_DAGANZO);
        guardar(
                List.of(base, Muestra.otraVersion(base, 2, "Otra del mismo órgano", List.of(), base.actualizadaEn())),
                List.of());
        assertThat(jdbc.sql("SELECT count(*) FROM organo").query(Long.class).single())
                .isEqualTo(1);
    }
}
