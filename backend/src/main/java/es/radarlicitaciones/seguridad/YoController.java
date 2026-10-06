package es.radarlicitaciones.seguridad;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Usuario")
class YoController {

    @GetMapping("/api/v1/yo")
    @Operation(
            summary = "El usuario de la sesión, tal como lo ve la API",
            security = @SecurityRequirement(name = "keycloak"))
    Usuario yo(@AuthenticationPrincipal Jwt token) {
        return Usuario.de(token);
    }
}
