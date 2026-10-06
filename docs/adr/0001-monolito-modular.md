# 1. Monolito modular con Spring Modulith

## Contexto

La aplicación tiene partes con ritmos muy distintos: una ingesta por lotes que corre una vez al día y mueve cientos
de miles de filas, una API de lectura muy consultada, alertas por usuario y el envío de correo. Hay que separarlas
para que no se enreden, pero el volumen (unas decenas de miles de licitaciones nuevas al mes) no justifica servicios
separados, con su red, sus despliegues y su consistencia eventual entre bases de datos.

## Decisión

Un único despliegue de Spring Boot, dividido en módulos: un paquete de primer nivel por módulo (`ingesta`,
`licitaciones`, `catalogos`, `alertas`, `correo`, `seguridad`, `compartido`). Cada módulo publica unos pocos tipos
en su paquete raíz y esconde el resto en subpaquetes. Lo garantiza Spring Modulith:

- `ModulosTest` verifica que nadie usa clases internas de otro módulo y que no hay ciclos. Si se rompe, falla la CI.
- La comunicación hacia atrás va por eventos: la ingesta no conoce las alertas, publica `IngestaCompletada` y el
  módulo de alertas la escucha (ver [0005](0005-avisos-fiables.md)).
- La documentación de los módulos (diagramas PlantUML y fichas) se genera en cada build, en
  `target/spring-modulith-docs`.

Las dependencias permitidas son estas:

```
ingesta      → licitaciones
alertas      → licitaciones, ingesta (solo el evento), correo, seguridad
licitaciones → catalogos
```

`compartido` y `seguridad` son módulos abiertos: piezas transversales como la página de resultados, los errores
RFC 9457 o el usuario de la sesión.

## Consecuencias

- Un solo artefacto que desplegar y una sola base de datos: las transacciones entre módulos son transacciones de
  verdad.
- Si un día un módulo necesitara escalar aparte (por ejemplo, la ingesta), sus límites ya están marcados y
  comprobados. Sacarlo sería mover un paquete y cambiar el transporte de un evento.
- La disciplina no depende de la buena voluntad: depende de un test.
