package es.radarlicitaciones.ingesta.codice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.radarlicitaciones.licitaciones.AnulacionLeida;
import es.radarlicitaciones.licitaciones.LicitacionLeida;
import es.radarlicitaciones.licitaciones.LicitacionLeida.TipoDocumento;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Con una página real del feed recortada: 10 licitaciones de tipos y estados distintos y 2 anulaciones. */
class LectorCodiceTest {

    private static List<ElementoDelFeed> elementos;
    private static String enlaceSiguiente;

    @BeforeAll
    static void leerMuestra() throws IOException {
        try (var muestra = LectorCodiceTest.class.getResourceAsStream("/codice/muestra.atom");
                var lector = new LectorCodice(muestra)) {
            elementos = new ArrayList<>();
            lector.forEachRemaining(elementos::add);
            enlaceSiguiente = lector.enlaceSiguiente().orElse(null);
        }
    }

    private static LicitacionLeida licitacion(long id) {
        return elementos.stream()
                .filter(ElementoDelFeed.Licitacion.class::isInstance)
                .map(e -> ((ElementoDelFeed.Licitacion) e).licitacion())
                .filter(l -> l.id() == id)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void leeTodasLasEntradasYAnulaciones() {
        assertThat(elementos).hasSize(12).noneMatch(ElementoDelFeed.Ilegible.class::isInstance);
        assertThat(elementos)
                .filteredOn(ElementoDelFeed.Anulacion.class::isInstance)
                .map(e -> ((ElementoDelFeed.Anulacion) e).anulacion())
                .containsExactly(
                        new AnulacionLeida(20571647, OffsetDateTime.parse("2026-10-05T14:58:56.605+02:00")),
                        new AnulacionLeida(20622483, OffsetDateTime.parse("2026-10-05T14:43:58.549+02:00")));
    }

    @Test
    void conoceElEnlaceALaPaginaAnterior() {
        assertThat(enlaceSiguiente)
                .isEqualTo("https://contrataciondelestado.es/sindicacion/sindicacion_643/"
                        + "licitacionesPerfilesContratanteCompleto3_20261005_201859.atom");
    }

    @Test
    void traduceLosDatosPrincipales() {
        var l = licitacion(19616106);
        assertThat(l.expediente()).isEqualTo("1481/2026");
        assertThat(l.titulo())
                .isEqualTo("Servicio de pólizas de seguros del Ayuntamiento de Daganzo de Arriba - CONC-330");
        assertThat(l.estado()).isEqualTo("RES");
        assertThat(l.tipoContrato()).isEqualTo("2");
        assertThat(l.subtipoContrato()).isEqualTo("6");
        assertThat(l.procedimiento()).isEqualTo("1");
        assertThat(l.importeSinIva()).isEqualByComparingTo("75050");
        assertThat(l.importeConIva()).isEqualByComparingTo("75050");
        assertThat(l.valorEstimado()).isEqualByComparingTo("150100");
        assertThat(l.cpv()).containsExactly("66510000", "66516000", "66512100", "66514110");
        assertThat(l.nuts()).isEqualTo("ES300");
        assertThat(l.lugar()).isEqualTo("Daganzo de Arriba, Madrid");
        assertThat(l.duracion()).isEqualTo("1 año");
        assertThat(l.financiacionUe()).isFalse();
        assertThat(l.actualizadaEn()).isEqualTo(OffsetDateTime.parse("2026-10-05T18:56:41.543+02:00"));
        assertThat(l.url()).startsWith("https://contrataciondelestado.es/wps/poc?uri=deeplink:detalle_licitacion");
    }

    @Test
    void elPlazoEsHoraPeninsularYLaPublicacionLaDelAnuncioDeLicitacion() {
        var l = licitacion(19616106);
        assertThat(l.plazoPresentacion()).isEqualTo(OffsetDateTime.parse("2026-05-20T23:59:00+02:00"));
        // Hay anuncios de adjudicación y formalización posteriores: cuenta el de licitación (DOC_CN).
        assertThat(l.fechaPublicacion()).isEqualTo(LocalDate.of(2026, 5, 5));
    }

    @Test
    void traduceElOrganoConSuJerarquia() {
        var organo = licitacion(19616106).organo();
        assertThat(organo.clave()).isEqualTo("PLAT:30695810113341");
        assertThat(organo.nombre()).isEqualTo("Junta de Gobierno del Ayuntamiento de Daganzo de Arriba");
        assertThat(organo.nif()).isEqualTo("P2805300G");
        assertThat(organo.dir3()).isEqualTo("L01280534");
        assertThat(organo.tipo()).isEqualTo("3");
        assertThat(organo.ciudad()).isEqualTo("Daganzo de Arriba");
        assertThat(organo.codigoPostal()).isEqualTo("28814");
        assertThat(organo.jerarquia())
                .containsExactly(
                        "Daganzo de Arriba",
                        "Ayuntamientos",
                        "Comunidad de Madrid",
                        "ENTIDADES LOCALES",
                        "Sector Público");
    }

    @Test
    void traduceLotesResultadosYDocumentos() {
        var l = licitacion(19616106);
        assertThat(l.lotes()).hasSize(5);
        var primero = l.lotes().getFirst();
        assertThat(primero.numero()).isEqualTo("1");
        assertThat(primero.objeto()).isEqualTo("Lote 1 - Seguro de Responsabilidad Civil");
        assertThat(primero.importeSinIva()).isEqualByComparingTo("26000");
        assertThat(primero.cpv()).containsExactly("66510000", "66516000");
        assertThat(primero.nuts()).isEqualTo("ES300");

        assertThat(l.resultados()).hasSize(5);
        var adjudicado = l.resultados().getFirst();
        assertThat(adjudicado.lote()).isEqualTo("1");
        assertThat(adjudicado.codigo()).isEqualTo("9");
        assertThat(adjudicado.fechaAdjudicacion()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(adjudicado.ofertasRecibidas()).isEqualTo(2);
        assertThat(adjudicado.adjudicatario()).isEqualTo("ALLIANZ,COMPAÑÍA DE SEGUROS Y REASEGUROS S.A.");
        assertThat(adjudicado.adjudicatarioNif()).isEqualTo("A28007748");
        assertThat(adjudicado.importeSinIva()).isEqualByComparingTo(new BigDecimal("15500"));
        assertThat(adjudicado.pyme()).isFalse();

        assertThat(l.documentos())
                .extracting(LicitacionLeida.DocumentoLeido::tipo)
                .startsWith(TipoDocumento.PLIEGO_ADMINISTRATIVO, TipoDocumento.PLIEGO_TECNICO, TipoDocumento.OTRO);
        assertThat(l.documentos().getFirst().nombre()).isEqualTo("20260406 PCAP Polizas Seguros conc-330.pdf");
        assertThat(l.documentos()).allMatch(d -> d.url().startsWith("https://contrataciondelestado.es/"));
    }

    @Test
    void detectaLaFinanciacionEuropeaYLasDuracionesEnMeses() {
        var conFeader = licitacion(20622371);
        assertThat(conFeader.estado()).isEqualTo("PUB");
        assertThat(conFeader.financiacionUe()).isTrue();
        assertThat(conFeader.duracion()).isEqualTo("6 meses");
        assertThat(licitacion(19534051).duracion()).isEqualTo("30 días");
    }

    @Test
    void unaLicitacionSinPlazoNoLoInventa() {
        assertThat(licitacion(20622376).plazoPresentacion()).isNull();
    }

    @Test
    void unaEntradaSinLoImprescindibleSeCuentaComoIlegibleSinPararLaLectura() {
        var xml = """
                <feed xmlns="http://www.w3.org/2005/Atom"
                      xmlns:cac-place-ext="urn:dgpe:names:draft:codice-place-ext:schema:xsd:CommonAggregateComponents-2">
                  <entry>
                    <id>https://contrataciondelestado.es/sindicacion/licitacionesPerfilContratante/1</id>
                    <updated>2026-10-05T10:00:00+02:00</updated>
                    <cac-place-ext:ContractFolderStatus/>
                  </entry>
                  <entry>
                    <id>https://contrataciondelestado.es/sindicacion/licitacionesPerfilContratante/2</id>
                    <updated>no es una fecha</updated>
                  </entry>
                </feed>
                """;
        var leidos = leer(xml);
        assertThat(leidos).hasSize(2).allMatch(ElementoDelFeed.Ilegible.class::isInstance);
        assertThat(((ElementoDelFeed.Ilegible) leidos.getFirst()).motivo()).contains("expediente");
    }

    @Test
    void noResuelveEntidadesExternas() {
        var xml = """
                <?xml version="1.0"?>
                <!DOCTYPE feed [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <feed xmlns="http://www.w3.org/2005/Atom"><entry><id>&xxe;</id></entry></feed>
                """;
        assertThatThrownBy(() -> leer(xml)).isInstanceOf(FicheroNoValido.class);
    }

    @Test
    void unXmlRotoParaLaLecturaDelFichero() {
        assertThatThrownBy(() -> leer("<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry>"))
                .isInstanceOf(FicheroNoValido.class);
    }

    private static List<ElementoDelFeed> leer(String xml) {
        try (var lector = new LectorCodice(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            var leidos = new ArrayList<ElementoDelFeed>();
            lector.forEachRemaining(leidos::add);
            return leidos;
        }
    }
}
