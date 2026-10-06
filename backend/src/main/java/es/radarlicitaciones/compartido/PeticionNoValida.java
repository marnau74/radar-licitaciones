package es.radarlicitaciones.compartido;

/** La petición no tiene sentido aunque cada campo sea válido por separado: la API responde 400. */
public class PeticionNoValida extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PeticionNoValida(String mensaje) {
        super(mensaje);
    }
}
