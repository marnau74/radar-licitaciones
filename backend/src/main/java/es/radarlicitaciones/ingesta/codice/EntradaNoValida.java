package es.radarlicitaciones.ingesta.codice;

/** Una entrada del feed a la que le falta algo imprescindible o lo trae mal formado. */
class EntradaNoValida extends RuntimeException {

    private static final long serialVersionUID = 1L;

    EntradaNoValida(String mensaje) {
        super(mensaje);
    }
}
