package es.radarlicitaciones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import es.radarlicitaciones.Muestra;
import es.radarlicitaciones.PruebaDeIntegracion;
import es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones;
import es.radarlicitaciones.licitaciones.RegistroDeLicitaciones;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La API de IA de punta a punta contra PostgreSQL con pgvector, con un modelo y un vectorizador falsos (sin red ni
 * coste) y la descarga del pliego simulada.
 */
@Import(IaApiTest.Falsos.class)
@TestPropertySource(
        properties = {
            "radar.ia.resumenes.por-usuario-y-dia=1",
            "radar.ia.embeddings.retraso-inicial=PT1H" // la indexación la lanzan los tests
        })
class IaApiTest extends PruebaDeIntegracion {

    static final AtomicInteger RESUMENES_HECHOS = new AtomicInteger();

    @TestConfiguration(proxyBeanMethods = false)
    static class Falsos {

        @Bean
        ModeloDeLenguaje modeloFalso() {
            return new ModeloDeLenguaje() {
                @Override
                public Resultado resumir(byte[] pdf, String contexto) {
                    RESUMENES_HECHOS.incrementAndGet();
                    return new Resultado(
                            new ResumenDePliego(
                                    "Resumen de: " + contexto,
                                    "40.000 €",
                                    "1 año",
                                    List.of(),
                                    List.of(),
                                    List.of(new ResumenDePliego.Criterio("Precio", "100 puntos", true)),
                                    List.of(),
                                    List.of("Plazo de presentación corto")),
                            "falso/modelo",
                            null);
                }

                @Override
                public String descripcion() {
                    return "falso/modelo";
                }
            };
        }

        /** Bolsa de palabras en 1024 dimensiones: textos con palabras en común quedan cerca. */
        @Bean
        Vectorizador vectorizadorFalso() {
            return new Vectorizador() {
                @Override
                public List<float[]> vectorizar(List<String> textos) {
                    var vectores = new ArrayList<float[]>();
                    for (var texto : textos) {
                        var v = new float[1024];
                        for (var palabra : texto.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")) {
                            if (palabra.length() > 3) {
                                v[Math.floorMod(palabra.hashCode(), 1024)] += 1;
                            }
                        }
                        v[1023] += 0.01f; // nunca el vector cero
                        vectores.add(v);
                    }
                    return vectores;
                }

                @Override
                public String modelo() {
                    return "falso/bolsa-de-palabras";
                }
            };
        }
    }

    @MockitoBean
    DescargadorDePliegos descargador;

    @Autowired
    RegistroDeLicitaciones registro;

    @Autowired
    BuscadorDeLicitaciones buscador;

    @Autowired
    BusquedaSemantica busqueda;

    @Autowired
    TransactionTemplate transaccion;

    @BeforeEach
    void preparar() {
        transaccion.executeWithoutResult(e -> registro.guardar(Muestra.licitaciones(), Muestra.anulaciones()));
        when(descargador.descargar(anyString())).thenReturn("%PDF-1.4 falso".getBytes(StandardCharsets.US_ASCII));
        RESUMENES_HECHOS.set(0);
    }

    private static RequestPostProcessor usuario(String id) {
        return jwt().jwt(token -> token.subject(id).claim("email", id + "@demo.example"));
    }

    private List<String> documentos(long id) {
        return buscador.documentos(id).orElseThrow().stream().map(d -> d.url()).toList();
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult pedirResumen(
            long id, String documento, RequestPostProcessor quien) {
        var peticion = mvc.post()
                .uri("/api/v1/licitaciones/" + id + "/resumenes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documento\": \"" + documento + "\"}");
        return (quien == null ? peticion : peticion.with(quien)).exchange();
    }

    @Test
    void elEstadoDiceQueFuncionesHay() {
        assertThat(mvc.get().uri("/api/v1/ia/estado").exchange())
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"resumenes": true, "modelo": "falso/modelo", "busqueda": true}
                        """);
    }

    @Test
    void resumirNecesitaSesionYSeHaceUnaSolaVezPorDocumento() throws Exception {
        var documento = documentos(Muestra.SEGUROS_DAGANZO).getFirst();

        assertThat(pedirResumen(Muestra.SEGUROS_DAGANZO, documento, null)).hasStatus(HttpStatus.UNAUTHORIZED);

        var primero = pedirResumen(Muestra.SEGUROS_DAGANZO, documento, usuario("ana"));
        assertThat(primero).hasStatusOk();
        assertThat((String) JsonPath.read(primero.getResponse().getContentAsString(), "$.resumen.objeto"))
                .startsWith("Resumen de: «")
                .contains("Documento:");

        // Otra persona (o la misma) lo vuelve a pedir: se sirve el guardado, sin llamar al modelo ni gastar límite
        assertThat(pedirResumen(Muestra.SEGUROS_DAGANZO, documento, usuario("luis")))
                .hasStatusOk();
        assertThat(RESUMENES_HECHOS).hasValue(1);

        assertThat(mvc.get()
                        .uri("/api/v1/licitaciones/" + Muestra.SEGUROS_DAGANZO + "/resumenes")
                        .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].modelo")
                .isEqualTo("falso/modelo");
    }

    @Test
    void soloSeResumenDocumentosDeLaLicitacionQueExiste() {
        assertThat(pedirResumen(Muestra.SEGUROS_DAGANZO, "https://contrataciondelestado.es/otro.pdf", usuario("ana")))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(pedirResumen(1, "https://contrataciondelestado.es/otro.pdf", usuario("ana")))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(RESUMENES_HECHOS).hasValue(0);
    }

    @Test
    void cadaUsuarioTieneUnLimiteDiarioDeResumenesNuevos() {
        var documentos = documentos(Muestra.SEGUROS_DAGANZO);
        assertThat(documentos).hasSizeGreaterThanOrEqualTo(2);

        assertThat(pedirResumen(Muestra.SEGUROS_DAGANZO, documentos.get(0), usuario("ana")))
                .hasStatusOk();
        assertThat(pedirResumen(Muestra.SEGUROS_DAGANZO, documentos.get(1), usuario("ana")))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        // El límite es por usuario
        assertThat(pedirResumen(Muestra.SEGUROS_DAGANZO, documentos.get(1), usuario("luis")))
                .hasStatusOk();
    }

    @Test
    void laBusquedaPorSignificadoDevuelveLoMasParecidoPrimeroYSinAnuladas() throws Exception {
        assertThat(busqueda.indexarPendientes())
                .isEqualTo(Muestra.licitaciones().size());
        assertThat(busqueda.indexarPendientes()).isZero(); // sigue donde lo dejó: nada nuevo

        var titulo = buscador.porIds(List.of(Muestra.SERVICIOS_EN_PLAZO_BARCELONA))
                .getFirst()
                .titulo();
        var respuesta = mvc.get()
                .uri("/api/v1/licitaciones/semantica")
                .param("q", titulo)
                .param("limite", "5")
                .exchange();
        assertThat(respuesta).hasStatusOk();
        var json = respuesta.getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(json, "$[0].licitacion.id")).longValue())
                .isEqualTo(Muestra.SERVICIOS_EN_PLAZO_BARCELONA);
        List<Number> similitudes = JsonPath.read(json, "$[*].similitud");
        assertThat(similitudes).extracting(Number::doubleValue).isSortedAccordingTo((a, b) -> Double.compare(b, a));
        List<Number> ids = JsonPath.read(json, "$[*].licitacion.id");
        assertThat(ids).extracting(Number::longValue).doesNotContain(Muestra.ANULADA);

        // Solo abiertas (por defecto) frente a todas: la de Daganzo ya cerró el plazo
        var daganzo =
                buscador.porIds(List.of(Muestra.SEGUROS_DAGANZO)).getFirst().titulo();
        List<Number> abiertas = JsonPath.read(
                mvc.get()
                        .uri("/api/v1/licitaciones/semantica")
                        .param("q", daganzo)
                        .exchange()
                        .getResponse()
                        .getContentAsString(),
                "$[*].licitacion.id");
        List<Number> todas = JsonPath.read(
                mvc.get()
                        .uri("/api/v1/licitaciones/semantica")
                        .param("q", daganzo)
                        .param("abiertas", "false")
                        .exchange()
                        .getResponse()
                        .getContentAsString(),
                "$[*].licitacion.id");
        assertThat(abiertas).extracting(Number::longValue).doesNotContain(Muestra.SEGUROS_DAGANZO);
        assertThat(todas).extracting(Number::longValue).first().isEqualTo(Muestra.SEGUROS_DAGANZO);
    }

    @Test
    void unaConsultaVaciaEsUnaPeticionNoValida() {
        assertThat(mvc.get()
                        .uri("/api/v1/licitaciones/semantica")
                        .param("q", " ")
                        .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
