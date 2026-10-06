package es.radarlicitaciones.ingesta.fuentes;

import es.radarlicitaciones.ingesta.codice.FicheroNoValido;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Una lista fija de ficheros ATOM: los de un ZIP (un paquete histórico), los de una carpeta o uno solo. Siempre en orden
 * de nombre, para que una ingesta interrumpida pueda seguir por el mismo fichero.
 */
public class FuenteDeFicheros implements Fuente {

    private final String descripcion;
    private final List<FicheroDeFeed> ficheros;
    private final ZipFile zip;

    private FuenteDeFicheros(String descripcion, List<FicheroDeFeed> ficheros, ZipFile zip) {
        this.descripcion = descripcion;
        this.ficheros = ficheros;
        this.zip = zip;
    }

    /** Un ZIP, una carpeta con ficheros .atom o un fichero .atom. */
    public static FuenteDeFicheros de(Path ruta, String descripcion) {
        try {
            if (Files.isDirectory(ruta)) {
                try (var contenido = Files.list(ruta)) {
                    var ficheros = contenido
                            .filter(FuenteDeFicheros::esAtom)
                            .sorted()
                            .map(FuenteDeFicheros::fichero)
                            .toList();
                    return new FuenteDeFicheros(descripcion, ficheros, null);
                }
            }
            if (!Files.isRegularFile(ruta)) {
                throw new FicheroNoValido("No existe el fichero " + ruta);
            }
            if (ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                var zip = new ZipFile(ruta.toFile());
                var ficheros = zip.stream()
                        .filter(e -> !e.isDirectory()
                                && e.getName().toLowerCase(Locale.ROOT).endsWith(".atom"))
                        .sorted(Comparator.comparing(ZipEntry::getName))
                        .map(e -> new FicheroDeFeed(e.getName(), () -> zip.getInputStream(e)))
                        .toList();
                return new FuenteDeFicheros(descripcion, ficheros, zip);
            }
            return new FuenteDeFicheros(descripcion, List.of(fichero(ruta)), null);
        } catch (IOException e) {
            throw new FicheroNoValido("No se puede abrir " + ruta, e);
        }
    }

    private static boolean esAtom(Path ruta) {
        return Files.isRegularFile(ruta)
                && ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".atom");
    }

    private static FicheroDeFeed fichero(Path ruta) {
        return new FicheroDeFeed(ruta.getFileName().toString(), () -> Files.newInputStream(ruta));
    }

    @Override
    public String descripcion() {
        return descripcion + " (" + ficheros.size() + " ficheros)";
    }

    @Override
    public Optional<FicheroDeFeed> fichero(int indice, FicheroLeido anterior) {
        return indice < ficheros.size() ? Optional.of(ficheros.get(indice)) : Optional.empty();
    }

    @Override
    public boolean reanudable() {
        return true;
    }

    @Override
    public void close() {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
