# Cambios

Formato de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con
[versionado semántico](https://semver.org/lang/es/).

## [0.1.0] - 2026-10-06

Primera versión.

### Añadido

- Ingesta de la Plataforma de Contratación con Spring Batch: feed vivo diario, paquetes históricos (año o mes) y
  ficheros locales. Lectura de CODICE en streaming, versión más reciente de cada licitación, anulaciones en cualquier
  orden y reanudación de paquetes.
- Catálogos oficiales de CODICE: tipos de contrato, procedimientos, estados, resultados, tipos de órgano, NUTS y CPV.
- API pública:
  - Búsqueda de texto completo en español con filtros (estado, plazo abierto, tipo, procedimiento, NUTS, CPV,
    importe, fechas, órgano, fondos UE), orden y paginación.
  - Ficha, cifras, catálogos, órganos y estado de la ingesta.
  - Documentación OpenAPI.
- Alertas por usuario (Keycloak), avisos tras cada ingesta y correo resumen con bandeja de salida y reintentos.
- Web en Angular 22:
  - Búsqueda con los filtros en la URL, ficha, cifras con gráficas propias, alertas con vista previa, avisos y página
    sobre los datos (con carga manual para administración).
  - Tema claro y oscuro; adaptada al móvil y accesible.
- Pruebas:
  - Backend con Testcontainers (PostgreSQL, Keycloak y Mailpit).
  - Frontend con Vitest.
  - De punta a punta con Playwright y axe.
- CI con CodeQL y Dependabot, imágenes en GHCR por versión y despliegue con Caddy.

[0.1.0]: https://github.com/marnau74/radar-licitaciones/releases/tag/v0.1.0
