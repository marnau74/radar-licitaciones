package es.radarlicitaciones.ia;

import es.radarlicitaciones.seguridad.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "IA", description = "Resumen de pliegos y búsqueda por significado (si el servidor las tiene activadas)")
class IaController {

    private final ServicioDeResumenes resumenes;
    private final BusquedaSemantica busqueda;

    IaController(ServicioDeResumenes resumenes, BusquedaSemantica busqueda) {
        this.resumenes = resumenes;
        this.busqueda = busqueda;
    }

    /** @param modelo con qué se resumen los pliegos (null si no se resumen) */
    record EstadoDeIa(boolean resumenes, String modelo, boolean busqueda) {}

    @GetMapping("/ia/estado")
    @Operation(summary = "Qué funciones de IA tiene este servidor")
    EstadoDeIa estado() {
        var modelo = resumenes.modeloActivo().orElse(null);
        return new EstadoDeIa(modelo != null, modelo, busqueda.activa());
    }

    @GetMapping("/licitaciones/{id}/resumenes")
    @Operation(summary = "Resúmenes ya hechos de los documentos de una licitación")
    List<ResumenGuardado> resumenesDe(@PathVariable long id) {
        return resumenes.deLicitacion(id);
    }

    record PeticionDeResumen(@NotBlank String documento) {}

    @PostMapping("/licitaciones/{id}/resumenes")
    @Operation(
            summary = "Resume un pliego de la licitación",
            description = "Necesita sesión. Si ya estaba resumido se devuelve el guardado; si no, cuenta contra el"
                    + " límite diario del usuario. Puede tardar un minuto.")
    ResumenGuardado resumir(
            @AuthenticationPrincipal Jwt token, @PathVariable long id, @Valid @RequestBody PeticionDeResumen peticion) {
        return resumenes.resumir(id, peticion.documento(), Usuario.de(token).id());
    }

    @GetMapping("/licitaciones/semantica")
    @Operation(
            summary = "Búsqueda por significado",
            description = "Encuentra licitaciones parecidas a lo que se describe aunque no compartan palabras.")
    List<BusquedaSemantica.Resultado> buscarPorSignificado(
            @RequestParam String q,
            @RequestParam(defaultValue = "true") boolean abiertas,
            @RequestParam(defaultValue = "20") int limite) {
        return busqueda.buscar(q, abiertas, limite);
    }
}
