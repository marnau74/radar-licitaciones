package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.ingesta.codice.ElementoDelFeed;
import es.radarlicitaciones.ingesta.codice.FicheroNoValido;
import es.radarlicitaciones.ingesta.codice.LectorCodice;
import es.radarlicitaciones.ingesta.fuentes.Fuente;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * Lee los elementos de todos los ficheros de una fuente, uno detrás de otro.
 *
 * <p>Guarda en el contexto del paso por qué fichero y elemento va. Si la ingesta de un paquete falla, al relanzarla
 * Spring Batch reabre el lector con ese contexto y se sigue desde el último lote guardado, no desde el principio (un mes
 * son casi 200 ficheros).
 */
class LectorDeFuente implements ItemStreamReader<ElementoDelFeed> {

    static final String FICHERO = "lector.fichero";
    static final String ELEMENTO = "lector.elemento";
    static final String FICHEROS_LEIDOS = "lector.ficherosLeidos";
    static final String MAS_RECIENTE = "lector.masReciente";

    private static final Logger log = LoggerFactory.getLogger(LectorDeFuente.class);

    private final Fuente fuente;

    private int indiceFichero;
    private int elementosDelFichero;
    private int ficherosLeidos;
    private OffsetDateTime masReciente;
    private OffsetDateTime masAntiguoDelFichero;
    private InputStream flujo;
    private LectorCodice lector;

    LectorDeFuente(Fuente fuente) {
        this.fuente = fuente;
    }

    @Override
    public void open(ExecutionContext contexto) {
        var saltar = 0;
        if (fuente.reanudable() && contexto.containsKey(FICHERO)) {
            indiceFichero = contexto.getInt(FICHERO);
            saltar = contexto.getInt(ELEMENTO);
            ficherosLeidos = contexto.getInt(FICHEROS_LEIDOS, indiceFichero);
            log.info("Se reanuda la ingesta en el fichero {} a partir del elemento {}", indiceFichero + 1, saltar + 1);
        }
        if (contexto.containsKey(MAS_RECIENTE)) {
            masReciente = OffsetDateTime.parse(contexto.getString(MAS_RECIENTE));
        }
        log.info("Ingesta desde {}", fuente.descripcion());
        abrir(indiceFichero, null);
        for (int i = 0; i < saltar && lector != null && lector.hasNext(); i++) {
            lector.next();
            elementosDelFichero++;
        }
    }

    @Override
    public ElementoDelFeed read() {
        while (lector != null) {
            if (lector.hasNext()) {
                var elemento = lector.next();
                elementosDelFichero++;
                anotarFecha(elemento);
                return elemento;
            }
            var leido = new Fuente.FicheroLeido(lector.enlaceSiguiente(), masAntiguoDelFichero);
            cerrarFichero();
            ficherosLeidos++;
            abrir(indiceFichero + 1, leido);
        }
        return null;
    }

    private void anotarFecha(ElementoDelFeed elemento) {
        var fecha = switch (elemento) {
            case ElementoDelFeed.Licitacion l -> l.licitacion().actualizadaEn();
            case ElementoDelFeed.Anulacion a -> a.anulacion().cuando();
            case ElementoDelFeed.Ilegible i -> null;
        };
        if (fecha != null) {
            if (masReciente == null || fecha.isAfter(masReciente)) {
                masReciente = fecha;
            }
            if (masAntiguoDelFichero == null || fecha.isBefore(masAntiguoDelFichero)) {
                masAntiguoDelFichero = fecha;
            }
        }
    }

    private void abrir(int indice, Fuente.FicheroLeido anterior) {
        indiceFichero = indice;
        elementosDelFichero = 0;
        masAntiguoDelFichero = null;
        var fichero = fuente.fichero(indice, anterior);
        if (fichero.isEmpty()) {
            lector = null;
            return;
        }
        log.info("Leyendo el fichero {}: {}", indice + 1, fichero.get().nombre());
        try {
            flujo = fichero.get().abrir();
            lector = new LectorCodice(flujo);
        } catch (IOException e) {
            throw new FicheroNoValido("No se puede abrir " + fichero.get().nombre(), e);
        }
    }

    @Override
    public void update(ExecutionContext contexto) {
        contexto.putInt(FICHERO, indiceFichero);
        contexto.putInt(ELEMENTO, elementosDelFichero);
        contexto.putInt(FICHEROS_LEIDOS, ficherosLeidos);
        if (masReciente != null) {
            contexto.putString(MAS_RECIENTE, masReciente.toString());
        }
    }

    @Override
    public void close() {
        cerrarFichero();
        fuente.close();
    }

    private void cerrarFichero() {
        if (lector != null) {
            lector.close();
            lector = null;
        }
        if (flujo != null) {
            try {
                flujo.close();
            } catch (IOException e) {
                throw new ItemStreamException("No se puede cerrar el fichero", e);
            } finally {
                flujo = null;
            }
        }
    }
}
