# 4. Seguridad con Keycloak y un solo origen

## Contexto

La búsqueda es pública. Las alertas y los avisos son de cada usuario, y lanzar ingestas es solo de administración.
No se quieren contraseñas propias ni sesiones en la API.

## Decisión

- **Keycloak** como proveedor de identidad (OIDC): registro, verificación del correo, recuperación de contraseña y
  protección contra fuerza bruta, sin escribir nada de eso.
- **La web usa el flujo de código con PKCE** (`angular-oauth2-oidc`), el recomendado para una aplicación de una sola
  página: no hay secretos en el navegador. El token se renueva con el refresh token.
- **La API es un resource server sin estado**. Valida la firma, el emisor y la audiencia (`radar-api`, que añade un
  mapper del cliente en Keycloak). Los roles del realm llegan en el claim `roles`. Al no haber cookies, no hay CSRF
  que proteger.
- **Reglas por ruta**, con todo lo no declarado denegado:
  - Público: búsqueda, fichas, cifras, catálogos, estado de la ingesta, documentación y salud.
  - Con sesión: alertas, avisos y `/yo`.
  - Rol `admin`: `/api/v1/admin/**` y el actuator.
- **Un solo dominio** en producción. Caddy reparte `/` a la web, `/api` a la API y `/auth` a Keycloak. No hay CORS,
  la CSP de la web puede ser estricta (`connect-src 'self'`) y el certificado HTTPS es uno. La API pide las claves
  de Keycloak por la red interna, aunque el emisor de los tokens sea la dirección pública. La consola de
  administración de Keycloak no se publica.
- **Nadie ve lo de otro**. Una alerta ajena responde 404, no 403: no se revela que existe.
- El interceptor de la web solo manda el token a la API, nunca a otro servidor. El destino al volver del inicio de
  sesión solo puede ser una ruta de la propia web, para no abrir un redirector.

## Pruebas

- Con tokens simulados (`spring-security-test`): reglas por ruta, 401 y 403, y que un usuario no ve ni borra lo de
  otro.
- Con un Keycloak real en Testcontainers y el mismo realm que en desarrollo: el token de un usuario lleva la
  audiencia y los roles donde la API los espera.
- De punta a punta con Playwright: el inicio de sesión real en Keycloak y la vuelta a la página pedida.
