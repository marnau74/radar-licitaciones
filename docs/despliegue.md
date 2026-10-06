# Despliegue

Un servidor con Docker y un dominio. Todo va bajo ese dominio con HTTPS automático (Caddy):

| Dirección | Qué es |
|---|---|
| `https://DOMINIO/` | la web |
| `https://DOMINIO/api/` | la API (documentación en `/api/docs`) |
| `https://DOMINIO/auth/` | Keycloak (inicio de sesión) |

## 1. Requisitos

- Un servidor con Docker y el plugin de compose. Con 2 GB de RAM basta para empezar; 4 GB si se cargan varios años
  de histórico.
- Un registro DNS del dominio apuntando al servidor, y los puertos 80 y 443 abiertos (Caddy pide el certificado a
  Let's Encrypt).
- Un servidor de correo SMTP con STARTTLS, para los avisos y para la verificación de cuentas de Keycloak.

## 2. Ficheros

En el servidor, en una carpeta (por ejemplo `/opt/radar`), se copia el contenido de `deploy/` del repositorio:

```
compose.yaml
Caddyfile
.env.ejemplo
keycloak/realm-radar.json
postgres/crear-base-keycloak.sh
```

## 3. Configuración

Se copia `.env.ejemplo` como `.env` y se rellena. Para las claves:

```bash
openssl rand -base64 32
```

El `.env` tiene claves: permisos `600` y nunca en el repositorio.

## 4. Arranque

```bash
docker compose up -d
docker compose ps
```

En el primer arranque:

- PostgreSQL crea la base de datos de la API y, aparte, la de Keycloak con su propio usuario.
- Keycloak importa el realm `radar`, sin cuentas de ejemplo y con verificación del correo obligatoria.
- La API aplica sus migraciones (Flyway) y programa la ingesta diaria (7:30, hora peninsular).

## 5. Primera cuenta de administración

La consola de Keycloak no se publica en internet. Se usa con un túnel SSH desde tu equipo:

```bash
ssh -L 8081:localhost:8081 usuario@servidor
```

Con el túnel abierto:

1. Abre <http://localhost:8081/auth/admin> y entra con `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_CLAVE`.
2. En el realm `radar`, crea tu cuenta (o regístrate desde la web) y asígnale el rol de realm `admin`.

## 6. Cargar el histórico

La ingesta diaria solo lee lo nuevo del feed. Para tener datos desde el principio:

1. Entra en la web con la cuenta de administración.
2. En «Los datos» → «Cargar datos ahora», lanza los paquetes que quieras: un año cerrado (`2025`) o los meses del
   año en curso (`202601`, `202602`…).

Cada paquete se descarga una vez, en el volumen `descargas`. Si una carga se corta, se reanuda donde se quedó
pidiendo otra vez el mismo periodo.

## 7. Actualizar

Las imágenes se publican con cada versión (`vX.Y.Z`), solo si pasa la CI. Para actualizar:

```bash
sed -i 's/^VERSION=.*/VERSION=0.2.0/' .env
docker compose pull && docker compose up -d
```

Las migraciones se aplican solas al arrancar la API.

## 8. Copias de seguridad

Toda la información está en PostgreSQL (licitaciones, alertas, avisos y, en su base, Keycloak):

```bash
docker compose exec -T postgres pg_dumpall -U radar | gzip > radar-$(date +%F).sql.gz
```

Las licitaciones se pueden volver a cargar desde la Plataforma. Lo que no se recupera sin copia son las cuentas,
las alertas y los avisos.

## 9. Vigilar

- `docker compose ps`: la API y la web tienen comprobación de salud.
- Resultado de cada ingesta: la tabla de «Los datos» en la web, o `GET /api/v1/admin/ingestas` (con el detalle de
  los errores).
- Correos sin enviar: tabla `correo_saliente` (`enviado_en IS NULL`, con `ultimo_error`).

## Una sola instancia

La API se despliega como una única instancia. La ingesta programada y la recuperación al arrancar (que marca como
fallida una ingesta cortada) lo dan por hecho. Para varias instancias habría que añadir un bloqueo distribuido
(por ejemplo, ShedLock sobre PostgreSQL) en la ingesta programada. El envío de correo ya es seguro con varias, porque
usa `SKIP LOCKED`.
