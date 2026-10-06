package es.radarlicitaciones.compartido;

/** La operación choca con el estado actual (una ingesta ya en marcha, un límite alcanzado): la API responde 409. */
public class Conflicto extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Conflicto(String mensaje) {
        super(mensaje);
    }
}
