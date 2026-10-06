package es.radarlicitaciones.catalogos;

/** Las listas de códigos disponibles y la tabla de cada una. */
public enum Catalogo {
    TIPO_CONTRATO("cat_tipo_contrato"),
    PROCEDIMIENTO("cat_procedimiento"),
    ESTADO("cat_estado"),
    RESULTADO("cat_resultado"),
    TIPO_ORGANO("cat_tipo_organo"),
    NUTS("cat_nuts"),
    CPV("cat_cpv");

    private final String tabla;

    Catalogo(String tabla) {
        this.tabla = tabla;
    }

    String tabla() {
        return tabla;
    }
}
