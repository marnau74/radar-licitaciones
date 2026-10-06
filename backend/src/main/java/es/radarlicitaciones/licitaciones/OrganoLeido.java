package es.radarlicitaciones.licitaciones;

import java.util.List;
import java.util.Objects;

/**
 * El órgano de contratación de una licitación, tal como viene en la fuente.
 *
 * @param clave identificador estable: el de la Plataforma, si no el DIR3, si no el NIF y el nombre
 * @param tipo código CODICE del tipo de administración (estatal, autonómica, local...)
 * @param jerarquia organismos de los que depende, del más cercano al más general
 */
public record OrganoLeido(
        String clave,
        String nombre,
        String nif,
        String dir3,
        String idPlataforma,
        String tipo,
        String ciudad,
        String codigoPostal,
        String web,
        String perfilContratante,
        List<String> jerarquia) {

    public OrganoLeido {
        Objects.requireNonNull(clave, "clave");
        Objects.requireNonNull(nombre, "nombre");
        jerarquia = List.copyOf(jerarquia);
    }
}
