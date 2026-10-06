# 2. Ingesta con Spring Batch y StAX

## Contexto

La Plataforma de Contratación publica las licitaciones como ATOM con el estándar CODICE (XML muy anidado):

- **Feed vivo**: páginas de 500 entradas de unos 12 MB, encadenadas hacia atrás en el tiempo con `link rel="next"`.
- **Paquetes históricos**: un ZIP por año y uno por mes del año en curso. Septiembre de 2026 son 300 MB
  comprimidos, 193 ficheros y 2,9 GB de XML.
- Cada cambio de una licitación (adjudicación, corrección de plazo, un pliego nuevo) es **una entrada nueva** con la
  misma `<id>` y otro `<updated>`. En un mes aparecen 96.000 entradas para 42.000 licitaciones.
- Las retiradas llegan como `at:deleted-entry`, a veces antes que la propia licitación en el orden de los ficheros.

## Decisión

Un job de Spring Batch con un único paso por lotes de 500 elementos:

- **Lectura en streaming con StAX**. Solo se construye en memoria la entrada que se está leyendo (un árbol de unas
  decenas de KB). El parser no admite DTD ni entidades externas: el XML viene de fuera (XXE). Un test lo comprueba.
- **Traducción tolerante**. Lo imprescindible (número, expediente, título, estado, órgano) tiene que estar; si
  falta, la entrada se cuenta como ilegible y se sigue. Lo opcional mal formado (un importe que no es un número, una
  fecha rota) se deja vacío en vez de perder la licitación. En la carga real de septiembre hubo 0 ilegibles.
- **Fuentes intercambiables**: feed vivo, paquete ZIP (se descarga y se reutiliza si es de un periodo cerrado) o
  fichero local (pruebas, demo y e2e).
- **Reanudable**. El lector guarda en el contexto del paso el fichero y el elemento por los que va, y Spring Batch lo
  confirma con cada lote. Un paquete que falla se reanuda donde se quedó al volver a pedirlo. El feed vivo no se
  reanuda, porque cambia cada pocos minutos: la siguiente ingesta vuelve a leer desde la marca.
- **Marca del feed**. Se guarda la entrada más reciente de la última ingesta del feed completada, y la siguiente
  retrocede páginas solo hasta pasarla. Sin marca, solo se leen unas pocas páginas: el histórico se carga con
  paquetes.
- **Descargas restringidas**. Solo por HTTPS y a los dominios de la Plataforma, también tras las redirecciones: el
  feed trae el enlace a la página siguiente y no se sigue uno a cualquier sitio (SSRF). Los fallos de red y los 5xx
  se reintentan tres veces.
- **Una ingesta a la vez**. Al arrancar, una ejecución que figura «en curso» se cortó con la aplicación: se marca como
  fallida para que no bloquee y, si era un paquete, se pueda reanudar.

## Alternativas descartadas

- **JAXB con las clases generadas del esquema CODICE**: cientos de clases para usar treinta campos, y cargaría
  ficheros enteros en memoria.
- **Un `@Scheduled` con un bucle a mano**: habría que reinventar los lotes transaccionales, la reanudación y el
  historial de ejecuciones, que Spring Batch ya resuelve y deja en sus tablas.

## Consecuencias

- Un mes completo se ingiere en unos 72 segundos (unas 1.300 entradas por segundo), con memoria constante.
- El historial técnico (pasos, lotes, errores) queda en las tablas de Spring Batch. El resumen para la web, en la
  tabla `ingesta`.
