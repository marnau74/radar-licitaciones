package es.radarlicitaciones.ingesta.codice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Un elemento XML ya leído, con sus hijos. Es un árbol mínimo de una sola entrada del feed (unas decenas de KB): el
 * fichero completo se lee en streaming y nunca está entero en memoria. Los elementos se buscan por su nombre local, sin
 * prefijo de espacio de nombres: en CODICE no hay dos elementos hermanos que solo se distingan por él.
 */
final class Nodo {

    private final String nombre;
    private final Map<String, String> atributos;
    private final List<Nodo> hijos = new ArrayList<>();
    private final StringBuilder texto = new StringBuilder();

    Nodo(String nombre, Map<String, String> atributos) {
        this.nombre = nombre;
        this.atributos = Map.copyOf(atributos);
    }

    String nombre() {
        return nombre;
    }

    void anadirHijo(Nodo hijo) {
        hijos.add(hijo);
    }

    void anadirTexto(String fragmento) {
        texto.append(fragmento);
    }

    /** El texto del elemento sin espacios sobrantes, o nada si está vacío. */
    Optional<String> texto() {
        var limpio = texto.toString().strip().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? Optional.empty() : Optional.of(limpio);
    }

    Optional<String> atributo(String nombreAtributo) {
        return Optional.ofNullable(atributos.get(nombreAtributo));
    }

    /** El primer descendiente que sigue el camino de nombres, de hijo en hijo. */
    Optional<Nodo> hijo(String... camino) {
        Nodo actual = this;
        for (var paso : camino) {
            actual = actual.hijos.stream()
                    .filter(h -> h.nombre.equals(paso))
                    .findFirst()
                    .orElse(null);
            if (actual == null) {
                return Optional.empty();
            }
        }
        return Optional.of(actual);
    }

    /** Todos los hijos directos con ese nombre. */
    List<Nodo> hijos(String nombreHijo) {
        return hijos.stream().filter(h -> h.nombre.equals(nombreHijo)).toList();
    }

    /** Todos los nodos al final del camino (cada paso puede repetirse). */
    List<Nodo> todos(String... camino) {
        List<Nodo> nivel = List.of(this);
        for (var paso : camino) {
            nivel = nivel.stream().flatMap(n -> n.hijos(paso).stream()).toList();
        }
        return nivel;
    }

    /** El primer descendiente con ese nombre, a cualquier profundidad (en profundidad, en orden del documento). */
    Optional<Nodo> buscar(String nombreBuscado) {
        for (var hijo : hijos) {
            if (hijo.nombre.equals(nombreBuscado)) {
                return Optional.of(hijo);
            }
            var encontrado = hijo.buscar(nombreBuscado);
            if (encontrado.isPresent()) {
                return encontrado;
            }
        }
        return Optional.empty();
    }

    /** El texto del primer descendiente del camino. */
    Optional<String> texto(String... camino) {
        return hijo(camino).flatMap(Nodo::texto);
    }
}
