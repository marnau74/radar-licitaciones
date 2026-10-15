package es.radarlicitaciones.ia;

import es.radarlicitaciones.compartido.PeticionNoValida;
import es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones;
import es.radarlicitaciones.licitaciones.LicitacionResumen;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Búsqueda por significado: la consulta se convierte en un vector y se buscan las licitaciones más cercanas. Además,
 * cada pocos minutos vectoriza las licitaciones guardadas desde la última vez (la primera vez, todas), en lotes y
 * siguiendo el orden de ingesta: si se corta, sigue donde se quedó.
 */
@Service
class BusquedaSemantica {

    static final String CURSOR = "vectores";
    static final int MAX_CONSULTA = 500;
    static final int MAX_RESULTADOS = 50;
    /** Lotes como mucho en cada pasada, para no tener el modelo ocupado sin parar con un histórico grande. */
    static final int LOTES_POR_PASADA = 100;

    private static final Logger log = LoggerFactory.getLogger(BusquedaSemantica.class);

    private final Optional<Vectorizador> vectorizador;
    private final RepositorioDeIa repositorio;
    private final BuscadorDeLicitaciones buscador;
    private final PropiedadesDeIa propiedades;
    private final Clock reloj;

    BusquedaSemantica(
            Optional<Vectorizador> vectorizador,
            RepositorioDeIa repositorio,
            BuscadorDeLicitaciones buscador,
            PropiedadesDeIa propiedades,
            Clock reloj) {
        this.vectorizador = vectorizador;
        this.repositorio = repositorio;
        this.buscador = buscador;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    boolean activa() {
        return vectorizador.isPresent();
    }

    record Resultado(LicitacionResumen licitacion, double similitud) {}

    List<Resultado> buscar(String consulta, boolean soloAbiertas, int limite) {
        var v = vectorizador.orElseThrow(() -> ErrorDeIa.desactivada("La búsqueda por significado"));
        if (consulta == null || consulta.isBlank() || consulta.length() > MAX_CONSULTA) {
            throw new PeticionNoValida("Escribe qué buscas (hasta " + MAX_CONSULTA + " caracteres)");
        }
        if (limite < 1 || limite > MAX_RESULTADOS) {
            throw new PeticionNoValida("El límite va de 1 a " + MAX_RESULTADOS);
        }
        var vector = v.vectorizar(List.of(consulta.strip())).getFirst();
        var cercanas = repositorio.cercanas(vector, soloAbiertas, OffsetDateTime.now(reloj), limite);
        var similitud = cercanas.stream()
                .collect(Collectors.toMap(RepositorioDeIa.Cercana::licitacionId, RepositorioDeIa.Cercana::similitud));
        var porId = buscador.porIds(similitud.keySet()).stream()
                .collect(Collectors.toMap(LicitacionResumen::id, Function.identity()));
        return cercanas.stream()
                .filter(c -> porId.containsKey(c.licitacionId()))
                .map(c -> new Resultado(porId.get(c.licitacionId()), c.similitud()))
                .toList();
    }

    @Scheduled(
            initialDelayString = "${radar.ia.embeddings.retraso-inicial:PT1M}",
            fixedDelayString = "${radar.ia.embeddings.cada:PT5M}")
    void indexarPeriodicamente() {
        if (activa()) {
            try {
                indexarPendientes();
            } catch (RuntimeException e) {
                log.warn("No se han podido vectorizar las licitaciones (se reintenta en la siguiente pasada)", e);
            }
        }
    }

    /** Vectoriza lo guardado desde la última vez. Devuelve cuántas licitaciones ha vectorizado. */
    synchronized int indexarPendientes() {
        var v = vectorizador.orElseThrow(() -> ErrorDeIa.desactivada("La búsqueda por significado"));
        var porLote = propiedades.embeddings().porLote();
        var total = 0;
        for (var lote = 0; lote < LOTES_POR_PASADA; lote++) {
            var posicion = repositorio.cursor(CURSOR);
            var textos = buscador.paraIndexar(posicion.ingeridaEn(), posicion.licitacionId(), porLote);
            if (textos.isEmpty()) {
                break;
            }
            var vectores = v.vectorizar(textos.stream().map(t -> t.texto()).toList());
            var ahora = OffsetDateTime.now(reloj);
            for (var i = 0; i < textos.size(); i++) {
                repositorio.guardarVector(textos.get(i), vectores.get(i), v.modelo(), ahora);
            }
            var ultimo = textos.getLast();
            repositorio.guardarCursor(CURSOR, new RepositorioDeIa.Posicion(ultimo.ingeridaEn(), ultimo.id()));
            total += textos.size();
        }
        if (total > 0) {
            log.info("Vectorizadas {} licitaciones ({} en total)", total, repositorio.vectoresGuardados());
        }
        return total;
    }
}
