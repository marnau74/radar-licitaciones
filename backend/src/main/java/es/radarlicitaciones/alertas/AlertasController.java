package es.radarlicitaciones.alertas;

import es.radarlicitaciones.compartido.Conflicto;
import es.radarlicitaciones.compartido.RecursoNoEncontrado;
import es.radarlicitaciones.seguridad.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Las alertas del usuario de la sesión. Nadie ve ni toca las de otro: para él, no existen (404). */
@RestController
@RequestMapping("/api/v1/alertas")
@Tag(name = "Alertas", description = "Búsquedas guardadas que avisan de las licitaciones nuevas")
@SecurityRequirement(name = "keycloak")
@Transactional
class AlertasController {

    static final int MAX_ALERTAS = 20;

    private final AlertaRepositorio alertas;
    private final Clock reloj;

    AlertasController(AlertaRepositorio alertas, Clock reloj) {
        this.alertas = alertas;
        this.reloj = reloj;
    }

    record VistaDeAlerta(
            long id,
            String nombre,
            String texto,
            List<String> tiposContrato,
            List<String> procedimientos,
            List<String> nuts,
            List<String> cpv,
            BigDecimal importeMinimo,
            BigDecimal importeMaximo,
            boolean soloFondosUe,
            boolean porCorreo,
            boolean activa,
            String correo,
            OffsetDateTime creadaEn,
            OffsetDateTime actualizadaEn) {

        static VistaDeAlerta de(Alerta a) {
            return new VistaDeAlerta(
                    a.getId(),
                    a.getNombre(),
                    a.getTexto(),
                    a.getTiposContrato(),
                    a.getProcedimientos(),
                    a.getNuts(),
                    a.getCpv(),
                    a.getImporteMinimo(),
                    a.getImporteMaximo(),
                    a.isSoloFondosUe(),
                    a.isPorCorreo(),
                    a.isActiva(),
                    a.getCorreo(),
                    a.getCreadaEn(),
                    a.getActualizadaEn());
        }
    }

    @GetMapping
    @Operation(summary = "Mis alertas")
    @Transactional(readOnly = true)
    List<VistaDeAlerta> mias(@AuthenticationPrincipal Jwt token) {
        return alertas.findByUsuarioIdOrderByCreadaEnDesc(token.getSubject()).stream()
                .map(VistaDeAlerta::de)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Una de mis alertas")
    @Transactional(readOnly = true)
    VistaDeAlerta una(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return VistaDeAlerta.de(buscar(token, id));
    }

    @PostMapping
    @Operation(summary = "Crea una alerta", description = "Como mucho " + MAX_ALERTAS + " por usuario.")
    ResponseEntity<VistaDeAlerta> crear(@AuthenticationPrincipal Jwt token, @Valid @RequestBody DatosDeAlerta datos) {
        var usuario = Usuario.de(token);
        if (alertas.countByUsuarioId(usuario.id()) >= MAX_ALERTAS) {
            throw new Conflicto("Ya tienes " + MAX_ALERTAS + " alertas: borra alguna para crear otra");
        }
        var alerta =
                alertas.save(new Alerta(usuario.id(), usuario.correoObligatorio(), datos, OffsetDateTime.now(reloj)));
        return ResponseEntity.created(URI.create("/api/v1/alertas/" + alerta.getId()))
                .body(VistaDeAlerta.de(alerta));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Modifica una alerta",
            description = "El correo de los avisos pasa a ser el actual de la cuenta.")
    VistaDeAlerta modificar(
            @AuthenticationPrincipal Jwt token, @PathVariable long id, @Valid @RequestBody DatosDeAlerta datos) {
        var usuario = Usuario.de(token);
        var alerta = buscar(token, id);
        alerta.actualizar(usuario.correoObligatorio(), datos, OffsetDateTime.now(reloj));
        return VistaDeAlerta.de(alertas.saveAndFlush(alerta));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borra una alerta y sus avisos")
    ResponseEntity<Void> borrar(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        alertas.delete(buscar(token, id));
        return ResponseEntity.noContent().build();
    }

    private Alerta buscar(Jwt token, long id) {
        return alertas.findByIdAndUsuarioId(id, token.getSubject())
                .orElseThrow(() -> new RecursoNoEncontrado("No tienes ninguna alerta con el número " + id));
    }
}
