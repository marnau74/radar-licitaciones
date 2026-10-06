# 5. Avisos fiables: evento persistido y bandeja de salida

## Contexto

Tras cada ingesta hay que avisar a cada usuario de lo nuevo que encaja con sus alertas. Fallos que no se pueden
permitir: perder avisos si la aplicación se para a medias, avisar dos veces de lo mismo, mandar un correo de algo
que luego se deshace, o bloquear la ingesta porque el servidor de correo no responde.

## Decisión

1. **El evento se guarda en la transacción de la ingesta**. Al terminar, `FinDeIngesta` guarda el resumen, avanza la
   marca del feed y publica `IngestaCompletada` en una sola transacción. Spring Modulith anota el evento en su
   registro de publicaciones (tabla `event_publication`). Si la aplicación se para antes de procesarlo, se vuelve a
   entregar al arrancar.
2. **El listener corre aparte**: `@ApplicationModuleListener`, asíncrono, tras confirmar la ingesta y en su propia
   transacción.
3. **Ningún aviso repetido**. `aviso` tiene una restricción única por alerta y licitación, y la inserción es
   `ON CONFLICT DO NOTHING RETURNING`. Solo lo realmente nuevo pasa al correo. Reintentar el evento no duplica nada.
4. **Ningún aviso perdido si una ingesta falla a medias**. El evento lleva el intervalo desde el final de la
   **anterior ingesta completada**, no desde el inicio de esta. Lo que guardó una ingesta fallida entra en la
   siguiente.
5. **Bandeja de salida**. El correo resumen (texto y HTML, uno por usuario y por ingesta) se encola en la misma
   transacción que los avisos. Un proceso aparte lo envía con `FOR UPDATE SKIP LOCKED` y reintenta con esperas
   crecientes, de un minuto a doce horas. Tras seis intentos deja de intentar y anota el error.
6. **Solo se avisa de lo útil**:
   - Licitaciones publicadas con el plazo abierto y no anuladas.
   - Publicadas desde la creación de la alerta y como mucho en la última semana: una alerta nueva no llega con
     cientos de licitaciones antiguas.
   - Hasta 100 por alerta y por ingesta.

## Consecuencias

- Un fallo del servidor de correo no afecta ni a la ingesta ni a los avisos en la web.
- El test de integración recorre todo el camino: el job ingiere la muestra, se generan los avisos y el correo llega
  de verdad a un Mailpit en Testcontainers. Repetir la ingesta no genera ni avisos ni correos nuevos.
