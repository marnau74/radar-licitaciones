package es.radarlicitaciones.ia;

import es.radarlicitaciones.compartido.PeticionNoValida;
import es.radarlicitaciones.compartido.RecursoNoEncontrado;
import es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resúmenes de pliegos. Cada documento se resume una sola vez: lo que ya está hecho se sirve a cualquiera sin coste.
 * Solo se resumen documentos publicados con la licitación (no cualquier URL), y lo nuevo cuenta contra el límite
 * diario de quien lo pide. La descarga y el modelo van fuera de cualquier transacción: pueden tardar minutos.
 */
@Service
class ServicioDeResumenes {

    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private static final Logger log = LoggerFactory.getLogger(ServicioDeResumenes.class);

    private final Optional<ModeloDeLenguaje> modelo;
    private final DescargadorDePliegos descargador;
    private final RepositorioDeIa repositorio;
    private final BuscadorDeLicitaciones buscador;
    private final PropiedadesDeIa propiedades;
    private final Clock reloj;

    ServicioDeResumenes(
            Optional<ModeloDeLenguaje> modelo,
            DescargadorDePliegos descargador,
            RepositorioDeIa repositorio,
            BuscadorDeLicitaciones buscador,
            PropiedadesDeIa propiedades,
            Clock reloj) {
        this.modelo = modelo;
        this.descargador = descargador;
        this.repositorio = repositorio;
        this.buscador = buscador;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    Optional<String> modeloActivo() {
        return modelo.map(ModeloDeLenguaje::descripcion);
    }

    List<ResumenGuardado> deLicitacion(long licitacionId) {
        return repositorio.resumenes(licitacionId);
    }

    ResumenGuardado resumir(long licitacionId, String url, String usuario) {
        var modelo = this.modelo.orElseThrow(() -> ErrorDeIa.desactivada("El resumen de pliegos"));
        var documentos = buscador.documentos(licitacionId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe la licitación " + licitacionId));
        var documento = documentos.stream()
                .filter(d -> d.url().equals(url))
                .findFirst()
                .orElseThrow(() -> new PeticionNoValida("Ese documento no está publicado con esta licitación"));

        var hecho = repositorio.resumen(licitacionId, url);
        if (hecho.isPresent()) {
            return hecho.get();
        }
        var limite = propiedades.resumenes().porUsuarioYDia();
        if (!repositorio.consumirUso(usuario, LocalDate.now(reloj.withZone(ZONA)), limite)) {
            throw ErrorDeIa.limiteDiario(limite);
        }

        var pdf = descargador.descargar(url);
        var licitacion = buscador.porIds(List.of(licitacionId)).getFirst();
        var contexto = "«%s» (%s). Documento: %s %s"
                .formatted(
                        licitacion.titulo(),
                        licitacion.organo(),
                        documento.tipo().replace('_', ' ').toLowerCase(java.util.Locale.ROOT),
                        documento.nombre() == null ? "" : "«" + documento.nombre() + "»");
        var inicio = System.nanoTime();
        var resultado = modelo.resumir(pdf, contexto);
        log.info(
                "Pliego resumido: licitación {}, {} KB, {} en {} s",
                licitacionId,
                pdf.length / 1024,
                resultado.modelo(),
                (System.nanoTime() - inicio) / 1_000_000_000);

        var guardado = new ResumenGuardado(
                licitacionId,
                url,
                documento.nombre(),
                resultado.modelo(),
                resultado.resumen(),
                resultado.aviso(),
                OffsetDateTime.now(reloj));
        repositorio.guardar(guardado, usuario);
        return repositorio.resumen(licitacionId, url).orElse(guardado);
    }
}
