package es.radarlicitaciones.licitaciones;

import java.util.List;

/** Guarda lo que llega de la fuente. Es idempotente: guardar dos veces lo mismo no cambia nada. */
public interface RegistroDeLicitaciones {

    /**
     * Guarda un lote de versiones y anulaciones en la transacción en curso. De cada licitación se queda con la versión
     * más reciente entre la guardada y las recibidas; el orden en que lleguen no importa.
     */
    ResultadoDelGuardado guardar(List<LicitacionLeida> versiones, List<AnulacionLeida> anulaciones);

    /**
     * Lo que ha cambiado al guardar un lote.
     *
     * @param nuevas licitaciones que no estaban
     * @param actualizadas licitaciones que estaban con una versión anterior
     * @param sinCambios versiones iguales o más antiguas que la guardada
     * @param anulaciones anulaciones recibidas (pueden ser de licitaciones que aún no han llegado)
     */
    record ResultadoDelGuardado(int nuevas, int actualizadas, int sinCambios, int anulaciones) {}
}
