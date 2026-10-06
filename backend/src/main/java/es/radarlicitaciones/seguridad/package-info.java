/**
 * Seguridad de la API: valida los tokens de Keycloak (OIDC) y decide qué es público, qué necesita sesión y qué es solo
 * de administración. Ofrece {@link es.radarlicitaciones.seguridad.Usuario} a los demás módulos.
 */
@ApplicationModule(displayName = "Seguridad", type = ApplicationModule.Type.OPEN)
package es.radarlicitaciones.seguridad;

import org.springframework.modulith.ApplicationModule;
