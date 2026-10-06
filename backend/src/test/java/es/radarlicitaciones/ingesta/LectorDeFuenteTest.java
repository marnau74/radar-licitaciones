package es.radarlicitaciones.ingesta;

import static org.assertj.core.api.Assertions.assertThat;

import es.radarlicitaciones.Muestra;
import es.radarlicitaciones.ingesta.codice.ElementoDelFeed;
import es.radarlicitaciones.ingesta.fuentes.FuenteDeFicheros;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class LectorDeFuenteTest {

    @TempDir
    Path carpeta;

    private Path carpetaConDosFicheros() throws IOException {
        Files.copy(Muestra.ruta(), carpeta.resolve("a.atom"));
        Files.copy(Muestra.ruta(), carpeta.resolve("b.atom"));
        Files.writeString(carpeta.resolve("notas.txt"), "no es un ATOM");
        return carpeta;
    }

    private static List<ElementoDelFeed> leer(LectorDeFuente lector, int cuantos) {
        var leidos = new ArrayList<ElementoDelFeed>();
        ElementoDelFeed elemento;
        while (leidos.size() < cuantos && (elemento = lector.read()) != null) {
            leidos.add(elemento);
        }
        return leidos;
    }

    @Test
    void leeTodosLosFicherosAtomDeUnaCarpetaEnOrden() throws IOException {
        var lector = new LectorDeFuente(FuenteDeFicheros.de(carpetaConDosFicheros(), "Prueba"));
        var contexto = new ExecutionContext();
        lector.open(contexto);
        var leidos = leer(lector, Integer.MAX_VALUE);
        lector.update(contexto);
        lector.close();

        assertThat(leidos).hasSize(24);
        assertThat(contexto.getInt(LectorDeFuente.FICHEROS_LEIDOS)).isEqualTo(2);
        assertThat(contexto.getString(LectorDeFuente.MAS_RECIENTE)).isEqualTo("2026-10-05T20:04:30.946+02:00");
    }

    @Test
    void alReanudarSigueDondeSeQuedo() throws IOException {
        var ruta = carpetaConDosFicheros();
        var contexto = new ExecutionContext();

        var primero = new LectorDeFuente(FuenteDeFicheros.de(ruta, "Prueba"));
        primero.open(contexto);
        var antes = leer(primero, 15); // el primer fichero entero y 3 elementos del segundo
        primero.update(contexto); // lo que Spring Batch guarda al confirmar cada lote
        primero.close();

        var segundo = new LectorDeFuente(FuenteDeFicheros.de(ruta, "Prueba"));
        segundo.open(contexto);
        var despues = leer(segundo, Integer.MAX_VALUE);
        segundo.close();

        assertThat(antes).hasSize(15);
        assertThat(despues).hasSize(9);
        assertThat(despues.getFirst()).isEqualTo(Muestra.elementos().get(3));
    }
}
