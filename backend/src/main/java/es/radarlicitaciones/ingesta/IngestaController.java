package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.compartido.PeticionNoValida;
import es.radarlicitaciones.ingesta.ServicioDeIngesta.ResumenDeIngesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ingesta", description = "Estado de la carga de datos y lanzamiento manual")
class IngestaController {

    private final ServicioDeIngesta servicio;

    IngestaController(ServicioDeIngesta servicio) {
        this.servicio = servicio;
    }

    record EstadoDeIngesta(boolean enCurso, long licitaciones, List<ResumenDeIngesta> ultimas) {}

    @GetMapping("/api/v1/ingesta/estado")
    @Operation(
            summary = "Estado de la carga de datos",
            description = "Las últimas ingestas y si hay una en curso (público).")
    EstadoDeIngesta estado() {
        return new EstadoDeIngesta(
                servicio.hayUnaEnCurso(),
                servicio.licitacionesGuardadas(),
                servicio.ultimas(10).stream().map(ResumenDeIngesta::publico).toList());
    }

    @GetMapping("/api/v1/admin/ingestas")
    @Operation(
            summary = "Últimas ingestas con el detalle de los errores",
            security = @SecurityRequirement(name = "keycloak"))
    List<ResumenDeIngesta> ultimas() {
        return servicio.ultimas(50);
    }

    /**
     * @param origen feed (lo nuevo desde la última vez) o paquete (un año o un mes completo)
     * @param periodo para un paquete: AAAA o AAAAMM
     */
    record NuevaIngesta(
            @NotNull @Pattern(regexp = "feed|paquete") String origen, String periodo) {}

    @PostMapping("/api/v1/admin/ingestas")
    @Operation(
            summary = "Lanza una ingesta",
            description = "Responde en cuanto empieza (202); su avance se ve en el estado. Solo administradores.",
            security = @SecurityRequirement(name = "keycloak"))
    ResponseEntity<Void> lanzar(@Valid @RequestBody NuevaIngesta peticion) {
        SolicitudDeIngesta solicitud = switch (peticion.origen()) {
            case "feed" -> new SolicitudDeIngesta.Feed();
            case "paquete" -> new SolicitudDeIngesta.Paquete(peticion.periodo());
            default -> throw new PeticionNoValida("Origen no válido");
        };
        servicio.lanzarEnSegundoPlano(solicitud);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/ingesta/estado"))
                .build();
    }
}
