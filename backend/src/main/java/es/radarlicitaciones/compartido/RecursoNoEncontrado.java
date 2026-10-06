package es.radarlicitaciones.compartido;

/** Lo que se pide no existe (o no es del usuario que lo pide): la API responde 404. */
public class RecursoNoEncontrado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecursoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
