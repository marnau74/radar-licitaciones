package es.radarlicitaciones.licitaciones.web;

import es.radarlicitaciones.compartido.Pagina;
import es.radarlicitaciones.compartido.RecursoNoEncontrado;
import es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones;
import es.radarlicitaciones.licitaciones.LicitacionResumen;
import es.radarlicitaciones.licitaciones.internal.DetalleDeLicitacion;
import es.radarlicitaciones.licitaciones.internal.DetalleJdbc;
import es.radarlicitaciones.licitaciones.internal.EstadisticasJdbc;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Licitaciones", description = "Búsqueda y ficha de las licitaciones (público)")
class LicitacionesController {

    // Los datos cambian con cada ingesta (una vez al día): un minuto de caché en el navegador no esconde nada.
    private static final CacheControl UN_MINUTO =
            CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic();

    private final BuscadorDeLicitaciones buscador;
    private final DetalleJdbc detalle;
    private final EstadisticasJdbc estadisticas;

    LicitacionesController(BuscadorDeLicitaciones buscador, DetalleJdbc detalle, EstadisticasJdbc estadisticas) {
        this.buscador = buscador;
        this.detalle = detalle;
        this.estadisticas = estadisticas;
    }

    @GetMapping("/licitaciones")
    @Operation(
            summary = "Busca licitaciones",
            description = "Texto completo en español sin tildes, filtros, orden y paginación.")
    ResponseEntity<Pagina<LicitacionResumen>> buscar(@Valid @ParameterObject ParametrosDeBusqueda parametros) {
        var pagina = buscador.buscar(
                parametros.filtro(),
                parametros.ordenDeBusqueda(),
                parametros.paginaPedida(),
                parametros.tamanoPedido());
        return ResponseEntity.ok().cacheControl(UN_MINUTO).body(pagina);
    }

    @GetMapping("/licitaciones/{id}")
    @Operation(summary = "Ficha de una licitación", description = "Con su órgano, lotes, resultados y documentos.")
    ResponseEntity<DetalleDeLicitacion> detalle(@PathVariable long id) {
        var licitacion = detalle.buscar(id)
                .orElseThrow(() -> new RecursoNoEncontrado("No hay ninguna licitación con el número " + id));
        return ResponseEntity.ok().cacheControl(UN_MINUTO).body(licitacion);
    }

    @GetMapping("/estadisticas")
    @Operation(summary = "Cifras del panel", description = "Se recalculan como mucho cada cinco minutos.")
    ResponseEntity<EstadisticasJdbc.Estadisticas> estadisticas() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(estadisticas.calcular());
    }
}
