-- Módulo ia: resúmenes de pliegos y búsqueda por significado.
-- La extensión viene en la imagen pgvector/pgvector:pg17 (PostgreSQL 17 con pgvector).
CREATE EXTENSION IF NOT EXISTS vector;

-- Un resumen por documento: se hace la primera vez que alguien lo pide y después se sirve guardado.
CREATE TABLE resumen_pliego (
    licitacion_id     bigint NOT NULL REFERENCES licitacion (id) ON DELETE CASCADE,
    documento_url     text NOT NULL,
    documento_nombre  text,
    modelo            text NOT NULL,       -- proveedor/modelo que lo hizo (p. ej. claude/claude-opus-5-5)
    resumen           jsonb NOT NULL,
    aviso             text,                -- p. ej. que el modelo local solo leyó una parte
    pedido_por        text NOT NULL,       -- sub de Keycloak de quien lo pidió
    creado_en         timestamptz NOT NULL,
    PRIMARY KEY (licitacion_id, documento_url)
);

-- Resúmenes nuevos pedidos por cada usuario y día (cada uno cuesta dinero o tiempo de máquina).
CREATE TABLE uso_ia (
    usuario    text NOT NULL,
    dia        date NOT NULL,
    resumenes  integer NOT NULL,
    PRIMARY KEY (usuario, dia)
);

-- Un vector por licitación (bge-m3: 1024 dimensiones), con lo necesario para filtrar sin ir a la tabla licitacion.
CREATE TABLE licitacion_vector (
    licitacion_id       bigint PRIMARY KEY REFERENCES licitacion (id) ON DELETE CASCADE,
    modelo              text NOT NULL,
    vector              vector(1024) NOT NULL,
    plazo_presentacion  timestamptz,
    anulada             boolean NOT NULL,
    indexada_en         timestamptz NOT NULL
);
-- HNSW con distancia coseno: búsqueda aproximada en milisegundos aunque haya cientos de miles de licitaciones.
CREATE INDEX licitacion_vector_hnsw ON licitacion_vector USING hnsw (vector vector_cosine_ops);

-- Hasta dónde se ha indexado: la última licitación guardada que ya tiene vector (en orden de ingesta).
CREATE TABLE ia_cursor (
    nombre         text PRIMARY KEY,
    ingerida_en    timestamptz NOT NULL,
    licitacion_id  bigint NOT NULL
);
