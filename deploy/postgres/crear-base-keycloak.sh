#!/bin/sh
# Keycloak tiene su propia base de datos y su propio usuario en el mismo PostgreSQL: la API no puede leer sus tablas
# ni al revés. Solo se ejecuta al crear el volumen por primera vez.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v clave="$KEYCLOAK_DB_CLAVE" <<'SQL'
CREATE ROLE keycloak LOGIN PASSWORD :'clave';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
REVOKE CONNECT ON DATABASE radar FROM PUBLIC;
SQL
