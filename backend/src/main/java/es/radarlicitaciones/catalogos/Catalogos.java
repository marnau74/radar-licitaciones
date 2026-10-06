package es.radarlicitaciones.catalogos;

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Consulta de los catálogos, siempre desde memoria (los CPV, los más grandes, son unos 9.500). */
@Service
public class Catalogos {

    private static final Comparator<Codigo> POR_NOMBRE =
            Comparator.comparing(Codigo::nombre, Collator.getInstance(Locale.of("es")));

    private final Map<Catalogo, Map<String, String>> nombres = new EnumMap<>(Catalogo.class);
    private final Map<String, Integer> nivelesNuts = new HashMap<>();

    Catalogos(JdbcClient jdbc) {
        for (var catalogo : Catalogo.values()) {
            var codigos = new LinkedHashMap<String, String>();
            jdbc.sql("SELECT codigo, nombre FROM " + catalogo.tabla()).query(rs -> {
                codigos.put(rs.getString(1), rs.getString(2));
            });
            nombres.put(catalogo, Map.copyOf(codigos));
        }
        jdbc.sql("SELECT codigo, nivel FROM cat_nuts").query(rs -> {
            nivelesNuts.put(rs.getString(1), rs.getInt(2));
        });
    }

    /** El código con su nombre, o nada si el código es nulo. */
    public Optional<Codigo> codigo(Catalogo catalogo, String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return Optional.of(new Codigo(codigo, nombres.get(catalogo).getOrDefault(codigo, codigo)));
    }

    public List<Codigo> codigos(Catalogo catalogo, Collection<String> codigos) {
        return codigos.stream()
                .map(c -> new Codigo(c, nombres.get(catalogo).getOrDefault(c, c)))
                .toList();
    }

    public boolean existe(Catalogo catalogo, String codigo) {
        return nombres.get(catalogo).containsKey(codigo);
    }

    /** Todos los códigos de un catálogo, ordenados por nombre. */
    public List<Codigo> todos(Catalogo catalogo) {
        return nombres.get(catalogo).entrySet().stream()
                .map(e -> new Codigo(e.getKey(), e.getValue()))
                .sorted(POR_NOMBRE)
                .toList();
    }

    /** Las regiones NUTS de un nivel (1 grupos de comunidades, 2 comunidades, 3 provincias), por nombre. */
    public List<Codigo> nuts(int nivel) {
        return todos(Catalogo.NUTS).stream()
                .filter(c -> nivelesNuts.getOrDefault(c.codigo(), 0) == nivel)
                .toList();
    }
}
