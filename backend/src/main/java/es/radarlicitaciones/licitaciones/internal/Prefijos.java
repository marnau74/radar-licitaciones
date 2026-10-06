package es.radarlicitaciones.licitaciones.internal;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prefijos de los códigos CPV y NUTS. Cada licitación guarda todos los prefijos de sus códigos y el filtro busca el
 * prefijo pedido con {@code &&} sobre un índice GIN: es exacto y no necesita {@code LIKE}.
 *
 * <p>Un CPV tiene 8 cifras y su jerarquía se lee quitando ceros por la derecha: 72000000 (servicios de TI) es el padre
 * de 72200000 (programación y consultoría), y este de 72210000. Por eso el filtro se normaliza quitando los ceros
 * finales: pedir 72000000 o 72 es lo mismo.
 */
public final class Prefijos {

    private static final Pattern CPV = Pattern.compile("^\\d{2,8}$");
    private static final Pattern NUTS = Pattern.compile("^[A-Z]{2}[0-9A-Z]{0,3}$");

    private Prefijos() {}

    static List<String> deCpv(Collection<String> codigos) {
        Set<String> prefijos = new LinkedHashSet<>();
        for (var codigo : codigos) {
            for (int longitud = 2; longitud <= codigo.length(); longitud++) {
                prefijos.add(codigo.substring(0, longitud));
            }
        }
        return List.copyOf(prefijos);
    }

    static List<String> deNuts(String codigo) {
        if (codigo == null) {
            return List.of();
        }
        Set<String> prefijos = new LinkedHashSet<>();
        for (int longitud = 2; longitud <= codigo.length(); longitud++) {
            prefijos.add(codigo.substring(0, longitud));
        }
        return List.copyOf(prefijos);
    }

    /** Un CPV o prefijo de CPV válido para filtrar: de 2 a 8 cifras. */
    public static boolean esCpvValido(String codigo) {
        return codigo != null && CPV.matcher(codigo).matches();
    }

    /** Un NUTS o prefijo de NUTS válido para filtrar: ES, ES5, ES51, ES511. */
    public static boolean esNutsValido(String codigo) {
        return codigo != null && NUTS.matcher(codigo).matches();
    }

    /** 72000000, 7200 y 72 se buscan igual: como 72. */
    static String normalizarCpv(String codigo) {
        var normalizado = codigo;
        while (normalizado.length() > 2 && normalizado.endsWith("0")) {
            normalizado = normalizado.substring(0, normalizado.length() - 1);
        }
        return normalizado;
    }
}
