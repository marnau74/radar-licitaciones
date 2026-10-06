package es.radarlicitaciones.seguridad;

import es.radarlicitaciones.compartido.PeticionNoValida;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * El usuario de la petición, sacado de su token. Los usuarios viven en Keycloak: la API solo guarda su identificador.
 *
 * @param id el claim {@code sub}: estable aunque cambie el correo
 * @param correo el claim {@code email}
 */
public record Usuario(String id, String correo, String nombre, boolean correoVerificado, List<String> roles) {

    public Usuario {
        Objects.requireNonNull(id, "id");
        roles = List.copyOf(roles);
    }

    public static Usuario de(Jwt token) {
        var roles = token.getClaimAsStringList("roles");
        return new Usuario(
                token.getSubject(),
                token.getClaimAsString("email"),
                token.getClaimAsString("name"),
                Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified")),
                roles == null ? List.of() : roles);
    }

    /** El correo al que mandar los avisos. Sin él (Keycloak lo permite) no se pueden crear alertas por correo. */
    public String correoObligatorio() {
        if (correo == null || correo.isBlank()) {
            throw new PeticionNoValida("Tu cuenta no tiene correo: añádelo en tu perfil para recibir avisos");
        }
        return correo;
    }

    public boolean esAdministrador() {
        return roles.contains(SeguridadConfig.ROL_ADMIN);
    }
}
