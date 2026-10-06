# 3. Modelo y búsqueda en PostgreSQL

## Contexto

Hay que guardar la versión más reciente de cada licitación aunque las versiones lleguen desordenadas, repetidas o
mezcladas con anulaciones. Además, hay que buscar con texto en español y con filtros jerárquicos: un CPV de 8
cifras cuelga de su grupo y su división, y una provincia, de su comunidad.

## Decisión

### Guardado

- Una fila por licitación con su `actualizada_en` (el `<updated>` de la versión guardada). Por cada lote se hacen
  unas pocas sentencias por lotes de JDBC, no una por fila con JPA:
  1. De cada licitación del lote se queda la versión más reciente.
  2. Se leen las versiones guardadas de esas licitaciones y se descartan las recibidas que no son más nuevas.
  3. Se guardan los órganos y luego las licitaciones con
     `INSERT … ON CONFLICT (id) DO UPDATE … WHERE licitacion.actualizada_en < EXCLUDED.actualizada_en`. Ni siquiera
     dos procesos a la vez pisarían una versión con otra anterior.
  4. Se rehacen los lotes y resultados solo de las licitaciones que de verdad se han escrito.
- **Anulaciones independientes del orden**. Se guardan en su propia tabla, y una licitación está anulada si hay una
  anulación posterior a su versión guardada. Da igual qué llegue antes. Si la Plataforma la vuelve a publicar
  después, deja de estar anulada. Hay un test para cada orden.
- **Códigos sin clave foránea a los catálogos**. Si la Plataforma añade un código, la licitación se guarda igual y se
  enseña el código. En la carga real todos los códigos estaban en los catálogos.
- JPA solo donde rinde: las alertas, que son pocas filas con edición y bloqueo optimista.

### Búsqueda

- **Texto completo** con `tsvector` y una configuración propia `radar_es` (`unaccent` y raíces del español), con
  índice GIN. El texto se calcula al guardar y pesa más donde importa más:
  - A: título y expediente.
  - B: órgano.
  - C: lotes y nombres de los CPV.
  - D: adjudicatarias.

  La consulta usa `websearch_to_tsquery`: admite «frases» y -exclusiones.
- **Filtros jerárquicos sin `LIKE`**. Cada licitación guarda todos los prefijos de sus CPV y de su NUTS
  (`66`, `665`, `6651`… y `ES`, `ES3`, `ES30`, `ES300`). Filtrar por «72» o por «ES51» es un `&&` sobre un índice
  GIN. El CPV pedido se normaliza quitando los ceros finales: 72000000 y 72 son lo mismo.
- **«Abiertas» además del estado**. La Plataforma mantiene una licitación como «publicada» hasta valorar las
  ofertas, aunque el plazo ya haya cerrado. En la carga real había casi el doble de publicadas que con el plazo
  abierto. El filtro `abiertas` (plazo sin cerrar o sin plazo publicado) es aparte, y la web distingue «En plazo» de
  «Plazo cerrado».
- La paginación se limita a los primeros 10.000 resultados: más allá es caro y nadie lo usa; mejor afinar.

### Alternativas descartadas

- **Elasticsearch u OpenSearch**: otro servicio que operar, sincronizar y vigilar. Para este volumen, PostgreSQL
  responde en 25 a 100 ms con los datos de un mes y da consistencia inmediata con lo guardado.

## Consecuencias

- Reingerir lo mismo no cambia nada y las cuentas de cada ingesta son exactas (nuevas, actualizadas, sin cambios).
- Las consultas dependientes de la hora reciben la hora del reloj de la aplicación, no `now()`. Así los tests fijan
  el día y no caducan.
