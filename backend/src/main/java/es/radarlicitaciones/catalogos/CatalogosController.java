package es.radarlicitaciones.catalogos;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogos")
@Tag(name = "Catálogos", description = "Listas de códigos oficiales con su nombre")
class CatalogosController {

    private static final CacheControl UN_DIA =
            CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final Catalogos catalogos;
    private final JdbcClient jdbc;

    CatalogosController(Catalogos catalogos, JdbcClient jdbc) {
        this.catalogos = catalogos;
        this.jdbc = jdbc;
    }

    record TodosLosCatalogos(
            List<Codigo> estados,
            List<Codigo> tiposContrato,
            List<Codigo> procedimientos,
            List<Codigo> resultados,
            List<Codigo> tiposOrgano,
            List<Codigo> comunidades,
            List<Codigo> provincias) {}

    @GetMapping
    @Operation(summary = "Todos los catálogos pequeños (los CPV se buscan aparte)")
    ResponseEntity<TodosLosCatalogos> todos() {
        return ResponseEntity.ok()
                .cacheControl(UN_DIA)
                .body(new TodosLosCatalogos(
                        catalogos.todos(Catalogo.ESTADO),
                        catalogos.todos(Catalogo.TIPO_CONTRATO),
                        catalogos.todos(Catalogo.PROCEDIMIENTO),
                        catalogos.todos(Catalogo.RESULTADO),
                        catalogos.todos(Catalogo.TIPO_ORGANO),
                        catalogos.nuts(2),
                        catalogos.nuts(3)));
    }

    @GetMapping("/cpv")
    @Operation(
            summary = "Busca códigos CPV",
            description = "Por las primeras cifras del código o por palabras del nombre, sin importar las tildes.")
    ResponseEntity<List<Codigo>> cpv(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limite) {
        var texto = q.strip();
        var consulta = texto.chars().allMatch(Character::isDigit)
                ? jdbc.sql(
                                "SELECT codigo, nombre FROM cat_cpv WHERE codigo LIKE :prefijo ORDER BY codigo LIMIT :limite")
                        .param("prefijo", texto + "%")
                // Primero los que contienen el texto tal cual; después, los parecidos (trigramas).
                : jdbc.sql("""
                                SELECT codigo, nombre FROM cat_cpv
                                WHERE unaccent(lower(nombre)) LIKE '%' || unaccent(lower(:texto)) || '%'
                                   OR nombre % :texto
                                ORDER BY unaccent(lower(nombre)) LIKE '%' || unaccent(lower(:texto)) || '%' DESC,
                                         similarity(nombre, :texto) DESC, codigo
                                LIMIT :limite
                                """).param("texto", texto);
        var encontrados = consulta.param("limite", limite)
                .query((rs, fila) -> new Codigo(rs.getString(1), rs.getString(2)))
                .list();
        return ResponseEntity.ok().cacheControl(UN_DIA).body(encontrados);
    }
}
