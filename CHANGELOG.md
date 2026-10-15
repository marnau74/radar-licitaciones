# Cambios

Formato de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con
[versionado semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Añadido

- **Resumen de pliegos con IA** en la ficha: objeto, presupuesto, duración, solvencia, criterios de adjudicación
  con su peso, garantías y puntos de atención. Con Claude (SDK oficial de Anthropic, PDF entero y salida
  estructurada, con reserva automática ante rechazos) o con un modelo local en Ollama. Cada pliego se resume una vez
  y se guarda; resumir uno nuevo necesita sesión y tiene un límite diario por usuario. Solo se descargan documentos
  de la licitación y de la Plataforma de Contratación, por HTTPS.
- **Búsqueda por significado** (`/significado` y `GET /api/v1/licitaciones/semantica`): embeddings locales con
  `bge-m3` en Ollama y PostgreSQL con pgvector (HNSW), vectorizando lo nuevo cada pocos minutos.
- `GET /api/v1/ia/estado` para saber qué funciones de IA tiene el servidor. Todo apagado por defecto (ADR 6).

### Cambiado

- PostgreSQL pasa a la imagen `pgvector/pgvector:pg17` en todos los entornos. Al actualizar un servidor existente
  hay que reindexar la base de datos una vez (ver docs/despliegue.md).

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
