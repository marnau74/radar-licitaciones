# syntax=docker/dockerfile:1
# Imagen de la web: los ficheros estáticos de Angular servidos por nginx sin privilegios. La configuración
# (dirección de Keycloak) se pone al arrancar con variables de entorno: la misma imagen vale para cualquier entorno.

FROM node:24-alpine AS construccion
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# config.json lo sirve nginx a partir de las variables (ver la plantilla): el de desarrollo no entra en la imagen.
RUN npm run build && rm dist/radar-web/browser/config.json

FROM nginxinc/nginx-unprivileged:1.30-alpine
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=construccion /src/dist/radar-web/browser /usr/share/nginx/html
ENV RADAR_OIDC_EMISOR=http://localhost:8180/realms/radar \
    RADAR_OIDC_CLIENTE=radar-web
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/salud || exit 1
