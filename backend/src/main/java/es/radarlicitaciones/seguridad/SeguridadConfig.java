package es.radarlicitaciones.seguridad;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Qué puede hacer cada uno.
 *
 * <ul>
 *   <li>Público: la búsqueda, las fichas, las cifras, los catálogos, el estado de la ingesta, la documentación de la API
 *       y la salud.
 *   <li>Con sesión: las alertas, los avisos y los datos propios.
 *   <li>Rol {@code admin}: lanzar ingestas y las métricas.
 * </ul>
 *
 * <p>Sin sesiones ni cookies: cada petición trae su token (Bearer), así que no hay CSRF que proteger.
 */
@Configuration(proxyBeanMethods = false)
class SeguridadConfig {

    static final String ROL_ADMIN = "admin";

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/licitaciones/**",
                                "/api/v1/estadisticas",
                                "/api/v1/catalogos/**",
                                "/api/v1/organos/**",
                                "/api/v1/ingesta/estado")
                        .permitAll()
                        .requestMatchers("/api/openapi/**", "/api/docs/**", "/api/docs", "/api/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**", "/actuator/**")
                        .hasRole(ROL_ADMIN)
                        .requestMatchers("/api/v1/alertas/**", "/api/v1/avisos/**", "/api/v1/yo")
                        .authenticated()
                        .requestMatchers("/error")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(
                        servidor -> servidor.jwt(jwt -> jwt.jwtAuthenticationConverter(new ConversorDeToken())))
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .headers(cabeceras ->
                        cabeceras.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /** Los roles del realm llegan en el claim {@code roles} (un mapper del cliente en Keycloak). */
    static final class ConversorDeToken implements Converter<Jwt, AbstractAuthenticationToken> {

        @Override
        public AbstractAuthenticationToken convert(Jwt token) {
            return new JwtAuthenticationToken(token, roles(token), token.getSubject());
        }

        private static Collection<GrantedAuthority> roles(Jwt token) {
            var roles = token.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                    .toList();
        }
    }

    @Bean
    OpenAPI documentacion() {
        return new OpenAPI()
                .info(new Info()
                        .title("Radar de licitaciones")
                        .version("v1")
                        .description(
                                "Licitaciones públicas de la Plataforma de Contratación del Sector Público: búsqueda,"
                                        + " fichas, cifras y alertas por correo. Datos del Ministerio de Hacienda, reutilizados"
                                        + " con atribución.")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes(
                                "keycloak",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Token de acceso de Keycloak (realm radar)")));
    }
}
