package es.radarlicitaciones.ingesta.codice;

/** El fichero entero no se puede leer (XML roto, ZIP corrupto...): a diferencia de una entrada, esto para la ingesta. */
public class FicheroNoValido extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FicheroNoValido(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public FicheroNoValido(String mensaje) {
        super(mensaje);
    }
}
