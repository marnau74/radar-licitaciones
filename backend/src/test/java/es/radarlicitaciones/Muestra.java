package es.radarlicitaciones;

import es.radarlicitaciones.ingesta.codice.ElementoDelFeed;
import es.radarlicitaciones.ingesta.codice.LectorCodice;
import es.radarlicitaciones.licitaciones.AnulacionLeida;
import es.radarlicitaciones.licitaciones.LicitacionLeida;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** La página real del feed de {@code src/test/resources/codice/muestra.atom}, ya leída. */
public final class Muestra {

    /** Licitaciones de la muestra. */
    public static final long SEGUROS_DAGANZO = 19616106;

    public static final long OBRAS_EN_PLAZO_CORUNA = 20622371;
    public static final long SERVICIOS_EN_PLAZO_BARCELONA = 20622372;
    public static final long ANULADA = 20622483;

    private Muestra() {}

    public static Path ruta() {
        try {
            return Path.of(Muestra.class.getResource("/codice/muestra.atom").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<ElementoDelFeed> elementos() {
        try (var flujo = Muestra.class.getResourceAsStream("/codice/muestra.atom");
                var lector = new LectorCodice(flujo)) {
            var elementos = new ArrayList<ElementoDelFeed>();
            lector.forEachRemaining(elementos::add);
            return elementos;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<LicitacionLeida> licitaciones() {
        return elementos().stream()
                .filter(ElementoDelFeed.Licitacion.class::isInstance)
                .map(e -> ((ElementoDelFeed.Licitacion) e).licitacion())
                .toList();
    }

    public static List<AnulacionLeida> anulaciones() {
        return elementos().stream()
                .filter(ElementoDelFeed.Anulacion.class::isInstance)
                .map(e -> ((ElementoDelFeed.Anulacion) e).anulacion())
                .toList();
    }

    public static LicitacionLeida licitacion(long id) {
        return licitaciones().stream().filter(l -> l.id() == id).findFirst().orElseThrow();
    }

    /** Otra versión de una licitación: con otro título, otros lotes y otra fecha de actualización. */
    public static LicitacionLeida otraVersion(
            LicitacionLeida l, long id, String titulo, List<LicitacionLeida.LoteLeido> lotes, OffsetDateTime cuando) {
        return new LicitacionLeida(
                id,
                l.expediente(),
                titulo,
                l.url(),
                l.estado(),
                l.tipoContrato(),
                l.subtipoContrato(),
                l.procedimiento(),
                l.organo(),
                l.importeSinIva(),
                l.importeConIva(),
                l.valorEstimado(),
                l.cpv(),
                l.nuts(),
                l.lugar(),
                l.plazoPresentacion(),
                l.fechaPublicacion(),
                l.duracion(),
                l.financiacionUe(),
                lotes,
                l.resultados(),
                l.documentos(),
                cuando);
    }
}
