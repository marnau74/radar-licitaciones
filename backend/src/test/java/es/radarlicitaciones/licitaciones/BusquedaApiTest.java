package es.radarlicitaciones.licitaciones;

import static es.radarlicitaciones.Muestra.ANULADA;
import static es.radarlicitaciones.Muestra.OBRAS_EN_PLAZO_CORUNA;
import static es.radarlicitaciones.Muestra.SEGUROS_DAGANZO;
import static es.radarlicitaciones.Muestra.SERVICIOS_EN_PLAZO_BARCELONA;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import es.radarlicitaciones.Muestra;
import es.radarlicitaciones.PruebaDeIntegracion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/** La API pública de búsqueda contra la muestra real guardada en PostgreSQL. */
class BusquedaApiTest extends PruebaDeIntegracion {

    @Autowired
    RegistroDeLicitaciones registro;

    @Autowired
    TransactionTemplate transaccion;

    @BeforeEach
    void guardarMuestra() {
        transaccion.executeWithoutResult(e -> registro.guardar(Muestra.licitaciones(), Muestra.anulaciones()));
    }

    private List<Integer> ids(String consulta) throws Exception {
        var respuesta = mvc.get().uri("/api/v1/licitaciones?" + consulta).exchange();
        assertThat(respuesta).hasStatusOk();
        return JsonPath.read(respuesta.getResponse().getContentAsString(), "$.elementos[*].id");
    }

    @Test
    void buscaPorTextoSinTildesYPorRaiz() throws Exception {
        // «polizas» encuentra «pólizas»; «seguro» encuentra «seguros».
        assertThat(ids("q=polizas")).containsExactly((int) SEGUROS_DAGANZO);
        assertThat(ids("q=seguro")).contains((int) SEGUROS_DAGANZO);
    }

    @Test
    void buscaTambienPorElOrganoYLosAdjudicatarios() throws Exception {
        assertThat(ids("q=Daganzo")).contains((int) SEGUROS_DAGANZO);
        assertThat(ids("q=allianz")).containsExactly((int) SEGUROS_DAGANZO);
    }

    @Test
    void filtraPorEstadoTipoYLugar() throws Exception {
        assertThat(ids("estado=PUB"))
                .containsExactlyInAnyOrder((int) OBRAS_EN_PLAZO_CORUNA, (int) SERVICIOS_EN_PLAZO_BARCELONA);
        assertThat(ids("tipo=3&nuts=ES111")).containsExactlyInAnyOrder(19534051, (int) OBRAS_EN_PLAZO_CORUNA);
        // ES30 es la Comunidad de Madrid entera: incluye ES300.
        assertThat(ids("nuts=ES30")).containsExactlyInAnyOrder((int) SEGUROS_DAGANZO, 20622374, 12786865);
        assertThat(ids("estado=PUB,EV")).hasSize(3);
    }

    @Test
    void soloLasQueAunAdmitenOfertas() throws Exception {
        // A las 10:00 del 6 de octubre: el plazo de los seguros de Daganzo cerró en mayo.
        assertThat(ids("abiertas=true&tamano=100"))
                .contains((int) OBRAS_EN_PLAZO_CORUNA, (int) SERVICIOS_EN_PLAZO_BARCELONA)
                .doesNotContain((int) SEGUROS_DAGANZO);
        assertThat(ids("estado=PUB&abiertas=true"))
                .containsExactlyInAnyOrder((int) OBRAS_EN_PLAZO_CORUNA, (int) SERVICIOS_EN_PLAZO_BARCELONA);
    }

    @Test
    void filtraPorCpvConCualquierNivelDeLaJerarquia() throws Exception {
        assertThat(ids("cpv=66")).containsExactly((int) SEGUROS_DAGANZO);
        assertThat(ids("cpv=66510000")).containsExactly((int) SEGUROS_DAGANZO);
        assertThat(ids("cpv=66516")).containsExactly((int) SEGUROS_DAGANZO);
    }

    @Test
    void filtraPorImporteYOrdena() throws Exception {
        var porImporte = ids("orden=importe&importeMin=40000");
        assertThat(porImporte).isNotEmpty().first().isEqualTo(20497652);
        assertThat(ids("importeMin=1000000")).isEmpty();
    }

    @Test
    void lasAnuladasNoSalenSalvoQueSePidan() throws Exception {
        assertThat(ids("tamano=100")).hasSize(9).doesNotContain((int) ANULADA);
        assertThat(ids("tamano=100&incluirAnuladas=true")).hasSize(10).contains((int) ANULADA);
    }

    @Test
    void paginaConElTotal() {
        var respuesta = mvc.get()
                .uri("/api/v1/licitaciones?tamano=4&pagina=3&orden=publicacion")
                .exchange();
        assertThat(respuesta).hasStatusOk();
        assertThat(respuesta).bodyJson().extractingPath("$.total").isEqualTo(9);
        assertThat(respuesta).bodyJson().extractingPath("$.paginas").isEqualTo(3);
        assertThat(respuesta).bodyJson().extractingPath("$.elementos").asArray().hasSize(1);
        assertThat(respuesta).headers().hasValue("Cache-Control", "max-age=60, public");
    }

    @Test
    void unaPaginaMasAllaDelFinalDevuelveElTotal() {
        var respuesta = mvc.get().uri("/api/v1/licitaciones?pagina=50").exchange();
        assertThat(respuesta).hasStatusOk();
        assertThat(respuesta).bodyJson().extractingPath("$.total").isEqualTo(9);
        assertThat(respuesta).bodyJson().extractingPath("$.elementos").asArray().isEmpty();
    }

    @Test
    void losParametrosNoValidosSeExplicanConProblemDetail() {
        var respuesta = mvc.get().uri("/api/v1/licitaciones?cpv=abc&tamano=500").exchange();
        assertThat(respuesta).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(respuesta)
                .bodyJson()
                .extractingPath("$.errores[*].campo")
                .asArray()
                .hasSize(2);
    }

    @Test
    void laFichaTraeLosNombresDeLosCodigos() {
        var respuesta = mvc.get().uri("/api/v1/licitaciones/" + SEGUROS_DAGANZO).exchange();
        assertThat(respuesta).hasStatusOk();
        var json = assertThat(respuesta).bodyJson();
        json.extractingPath("$.estado.nombre").isEqualTo("Resuelta");
        json.extractingPath("$.tipoContrato.nombre").isEqualTo("Servicios");
        json.extractingPath("$.procedimiento.nombre").isEqualTo("Abierto");
        json.extractingPath("$.organo.tipo.nombre").isEqualTo("Autoridad local");
        json.extractingPath("$.nuts.nombre").isEqualTo("Madrid");
        json.extractingPath("$.cpv[0].codigo").isEqualTo("66510000");
        json.extractingPath("$.lotes").asArray().hasSize(5);
        json.extractingPath("$.resultados[0].resultado.nombre").isEqualTo("Formalizado");
        json.extractingPath("$.documentos[0].tipo").isEqualTo("PLIEGO_ADMINISTRATIVO");
    }

    @Test
    void unaLicitacionQueNoExisteDa404() {
        var respuesta = mvc.get().uri("/api/v1/licitaciones/1").exchange();
        assertThat(respuesta).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(respuesta).bodyJson().extractingPath("$.detail").asString().contains("1");
    }

    @Test
    void lasCifrasCuentanLasQueSiguenEnPlazo() {
        var respuesta = mvc.get().uri("/api/v1/estadisticas").exchange();
        assertThat(respuesta).hasStatusOk();
        var json = assertThat(respuesta).bodyJson();
        json.extractingPath("$.total").isEqualTo(10);
        json.extractingPath("$.enPlazo").isEqualTo(2);
        json.extractingPath("$.publicadasPorMes").asArray().hasSize(12);
    }

    @Test
    void losCatalogosYElBuscadorDeCpv() {
        assertThat(mvc.get().uri("/api/v1/catalogos").exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.estados[?(@.codigo == 'PUB')].nombre")
                .asArray()
                .containsExactly("Publicada");
        assertThat(mvc.get().uri("/api/v1/catalogos/cpv?q=7221").exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].codigo")
                .asArray()
                .isNotEmpty()
                .allMatch(c -> ((String) c).startsWith("7221"));
        assertThat(mvc.get().uri("/api/v1/catalogos/cpv?q=pólizas de seguros").exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isNotEmpty();
    }

    @Test
    void buscaOrganosPorParecido() {
        assertThat(mvc.get().uri("/api/v1/organos?q=daganzo").exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].nombre")
                .isEqualTo("Junta de Gobierno del Ayuntamiento de Daganzo de Arriba");
    }
}
