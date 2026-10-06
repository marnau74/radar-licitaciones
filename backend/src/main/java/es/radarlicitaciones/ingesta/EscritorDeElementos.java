package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.ingesta.codice.ElementoDelFeed;
import es.radarlicitaciones.licitaciones.AnulacionLeida;
import es.radarlicitaciones.licitaciones.LicitacionLeida;
import es.radarlicitaciones.licitaciones.RegistroDeLicitaciones;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Entrega cada lote al módulo de licitaciones y va sumando lo que ha cambiado en el contexto del paso, que Spring Batch
 * guarda en la misma transacción que el lote: las cuentas cuadran aunque la ingesta se corte y se reanude.
 */
class EscritorDeElementos implements ItemWriter<ElementoDelFeed>, StepExecutionListener {

    static final String NUEVAS = "escritor.nuevas";
    static final String ACTUALIZADAS = "escritor.actualizadas";
    static final String SIN_CAMBIOS = "escritor.sinCambios";
    static final String ANULADAS = "escritor.anuladas";
    static final String ILEGIBLES = "escritor.ilegibles";

    private static final Logger log = LoggerFactory.getLogger(EscritorDeElementos.class);
    /** Las primeras ilegibles se registran con detalle; si hay muchas, el resto solo se cuenta. */
    private static final int ILEGIBLES_CON_DETALLE = 20;

    private final RegistroDeLicitaciones registro;
    private StepExecution paso;

    EscritorDeElementos(RegistroDeLicitaciones registro) {
        this.registro = registro;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.paso = stepExecution;
    }

    @Override
    public void write(Chunk<? extends ElementoDelFeed> lote) {
        var versiones = new ArrayList<LicitacionLeida>();
        var anulaciones = new ArrayList<AnulacionLeida>();
        var ilegibles = 0;
        for (var elemento : lote) {
            switch (elemento) {
                case ElementoDelFeed.Licitacion l -> versiones.add(l.licitacion());
                case ElementoDelFeed.Anulacion a -> anulaciones.add(a.anulacion());
                case ElementoDelFeed.Ilegible i -> {
                    var anteriores = paso.getExecutionContext().getInt(ILEGIBLES, 0) + ilegibles;
                    if (anteriores < ILEGIBLES_CON_DETALLE) {
                        log.warn("Entrada ilegible {}: {}", i.idEntrada(), i.motivo());
                    } else {
                        log.debug("Entrada ilegible {}: {}", i.idEntrada(), i.motivo());
                    }
                    ilegibles++;
                }
            }
        }
        var resultado = registro.guardar(versiones, anulaciones);
        sumar(NUEVAS, resultado.nuevas());
        sumar(ACTUALIZADAS, resultado.actualizadas());
        sumar(SIN_CAMBIOS, resultado.sinCambios());
        sumar(ANULADAS, resultado.anulaciones());
        sumar(ILEGIBLES, ilegibles);
    }

    private void sumar(String clave, int cantidad) {
        var contexto = paso.getExecutionContext();
        contexto.putInt(clave, contexto.getInt(clave, 0) + cantidad);
    }
}
