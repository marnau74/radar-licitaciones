package es.radarlicitaciones.licitaciones.web;

import es.radarlicitaciones.compartido.RecursoNoEncontrado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organos")
@Tag(name = "Órganos de contratación", description = "Para filtrar la búsqueda por órgano (público)")
@Transactional(readOnly = true)
class OrganosController {

    private static final CacheControl UNA_HORA =
            CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    record OrganoResumen(long id, String nombre, String ciudad, long licitaciones) {}

    private final JdbcClient jdbc;

    OrganosController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Operation(summary = "Busca órganos de contratación por nombre", description = "Por parecido: tolera erratas.")
    ResponseEntity<List<OrganoResumen>> buscar(
            @RequestParam @NotBlank @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limite) {
        var organos = jdbc.sql("""
                        SELECT o.id, o.nombre, o.ciudad,
                               (SELECT count(*) FROM licitacion l WHERE l.organo_id = o.id) AS licitaciones
                        FROM organo o
                        WHERE o.nombre ILIKE '%' || :q || '%' OR o.nombre % :q
                        ORDER BY o.nombre ILIKE '%' || :q || '%' DESC, similarity(o.nombre, :q) DESC, o.id
                        LIMIT :limite
                        """)
                .param("q", q.strip())
                .param("limite", limite)
                .query((rs, fila) -> new OrganoResumen(
                        rs.getLong("id"), rs.getString("nombre"), rs.getString("ciudad"), rs.getLong("licitaciones")))
                .list();
        return ResponseEntity.ok().cacheControl(UNA_HORA).body(organos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Un órgano de contratación")
    ResponseEntity<OrganoResumen> uno(@PathVariable long id) {
        var organo = jdbc.sql("""
                        SELECT o.id, o.nombre, o.ciudad,
                               (SELECT count(*) FROM licitacion l WHERE l.organo_id = o.id) AS licitaciones
                        FROM organo o WHERE o.id = ?
                        """)
                .param(id)
                .query((rs, fila) -> new OrganoResumen(
                        rs.getLong("id"), rs.getString("nombre"), rs.getString("ciudad"), rs.getLong("licitaciones")))
                .optional()
                .orElseThrow(() -> new RecursoNoEncontrado("No hay ningún órgano con el número " + id));
        return ResponseEntity.ok().cacheControl(UNA_HORA).body(organo);
    }
}
