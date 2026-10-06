-- Esquema de las licitaciones (ver docs/adr/0003-modelo-y-busqueda.md).
--
-- Los códigos que manda la fuente (tipo de contrato, procedimiento, estado...) no llevan clave
-- foránea a los catálogos: si la Plataforma añade un código nuevo, la licitación se guarda igual
-- y se enseña el código tal cual. Lo vigila un test contra los datos reales.

CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Búsqueda en español sin tildes: «adjudicacion» encuentra «adjudicación» y «obras» encuentra «obra».
CREATE TEXT SEARCH CONFIGURATION radar_es (COPY = pg_catalog.spanish);
ALTER TEXT SEARCH CONFIGURATION radar_es
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, spanish_stem;

-- Catálogos oficiales de CODICE (se rellenan en V2).
CREATE TABLE cat_tipo_contrato (codigo text PRIMARY KEY, nombre text NOT NULL);
CREATE TABLE cat_procedimiento (codigo text PRIMARY KEY, nombre text NOT NULL);
CREATE TABLE cat_estado        (codigo text PRIMARY KEY, nombre text NOT NULL);
CREATE TABLE cat_resultado     (codigo text PRIMARY KEY, nombre text NOT NULL);
CREATE TABLE cat_tipo_organo   (codigo text PRIMARY KEY, nombre text NOT NULL);
CREATE TABLE cat_nuts (
    codigo text PRIMARY KEY,
    nombre text NOT NULL,
    nivel  smallint NOT NULL CHECK (nivel BETWEEN 0 AND 3)   -- 0 país, 1 grupo de regiones, 2 comunidad, 3 provincia
);
CREATE TABLE cat_cpv (
    codigo text PRIMARY KEY CHECK (codigo ~ '^[0-9]{8}$'),
    nombre text NOT NULL
);
CREATE INDEX cat_cpv_nombre_trgm ON cat_cpv USING gin (nombre gin_trgm_ops);

-- Órganos de contratación. La clave es el identificador más estable que traiga la fuente:
-- el de la Plataforma, si no el DIR3, si no el NIF y el nombre.
CREATE TABLE organo (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave              text NOT NULL UNIQUE,
    nombre             text NOT NULL,
    nif                text,
    dir3               text,
    id_plataforma      text,
    tipo               text,
    ciudad             text,
    codigo_postal      text,
    web                text,
    perfil_contratante text,
    jerarquia          text[] NOT NULL DEFAULT '{}',
    actualizado_en     timestamptz NOT NULL
);
CREATE INDEX organo_nombre_trgm ON organo USING gin (nombre gin_trgm_ops);

-- Una fila por licitación, con su versión más reciente. La fuente publica una entrada por cada
-- modificación: solo se guarda si es más nueva que la que hay (columna actualizada_en).
CREATE TABLE licitacion (
    id                     bigint PRIMARY KEY,           -- número final del <id> de la entrada ATOM
    expediente             text NOT NULL,
    titulo                 text NOT NULL,
    url                    text,
    estado                 text NOT NULL,
    tipo_contrato          text,
    subtipo_contrato       text,
    procedimiento          text,
    organo_id              bigint NOT NULL REFERENCES organo (id),
    importe_sin_iva        numeric(18, 2),
    importe_con_iva        numeric(18, 2),
    valor_estimado         numeric(18, 2),
    cpv                    text[] NOT NULL DEFAULT '{}',
    -- Todos los prefijos de cada CPV y NUTS (división, grupo, clase...; comunidad, provincia...):
    -- filtrar por «72» o por «ES61» es un && sobre un índice GIN, sin LIKE.
    cpv_prefijos           text[] NOT NULL DEFAULT '{}',
    nuts                   text,
    nuts_prefijos          text[] NOT NULL DEFAULT '{}',
    lugar                  text,
    plazo_presentacion     timestamptz,
    fecha_publicacion      date,
    duracion               text,
    financiacion_ue        boolean NOT NULL DEFAULT false,
    num_lotes              smallint NOT NULL DEFAULT 0,
    documentos             jsonb NOT NULL DEFAULT '[]',
    actualizada_en         timestamptz NOT NULL,         -- <updated> de la versión guardada
    vista_por_primera_vez  timestamptz NOT NULL,
    ingerida_en            timestamptz NOT NULL,
    anulada                boolean NOT NULL DEFAULT false,
    busqueda               tsvector NOT NULL
);
CREATE INDEX licitacion_busqueda       ON licitacion USING gin (busqueda);
CREATE INDEX licitacion_cpv_prefijos   ON licitacion USING gin (cpv_prefijos);
CREATE INDEX licitacion_nuts_prefijos  ON licitacion USING gin (nuts_prefijos);
CREATE INDEX licitacion_estado_plazo   ON licitacion (estado, plazo_presentacion);
CREATE INDEX licitacion_publicacion    ON licitacion (fecha_publicacion DESC);
CREATE INDEX licitacion_importe        ON licitacion (importe_sin_iva);
CREATE INDEX licitacion_organo         ON licitacion (organo_id);
CREATE INDEX licitacion_ingerida       ON licitacion (ingerida_en);
CREATE INDEX licitacion_expediente     ON licitacion (expediente);

CREATE TABLE lote (
    licitacion_id   bigint NOT NULL REFERENCES licitacion (id) ON DELETE CASCADE,
    numero          text NOT NULL,
    objeto          text,
    importe_sin_iva numeric(18, 2),
    cpv             text[] NOT NULL DEFAULT '{}',
    nuts            text,
    PRIMARY KEY (licitacion_id, numero)
);

CREATE TABLE resultado (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    licitacion_id        bigint NOT NULL REFERENCES licitacion (id) ON DELETE CASCADE,
    lote                 text,
    codigo               text,
    fecha_adjudicacion   date,
    ofertas_recibidas    integer,
    adjudicatario        text,
    adjudicatario_nif    text,
    importe_sin_iva      numeric(18, 2),
    importe_con_iva      numeric(18, 2),
    pyme                 boolean
);
CREATE INDEX resultado_licitacion ON resultado (licitacion_id);
CREATE INDEX resultado_adjudicatario_nif ON resultado (adjudicatario_nif);

-- Anulaciones (at:deleted-entry). Se guardan aparte porque pueden llegar antes que la licitación
-- (los ficheros históricos no vienen en orden): al guardar una versión se mira si hay una
-- anulación posterior.
CREATE TABLE anulacion (
    licitacion_id bigint PRIMARY KEY,
    cuando        timestamptz NOT NULL
);

-- Hasta dónde se ha leído el feed vivo: la próxima ingesta diaria se para al llegar aquí.
CREATE TABLE ingesta_marca (
    origen         text PRIMARY KEY,
    actualizado_en timestamptz NOT NULL
);

-- Resumen de cada ingesta, para la API pública y el panel. El detalle técnico (pasos, reintentos)
-- está en las tablas de Spring Batch.
CREATE TABLE ingesta (
    id            bigint PRIMARY KEY,                -- id de la ejecución del job de Spring Batch
    origen        text NOT NULL,
    descripcion   text NOT NULL,
    inicio        timestamptz NOT NULL,
    fin           timestamptz NOT NULL,
    estado        text NOT NULL,                     -- COMPLETED, FAILED, STOPPED...
    ficheros      integer NOT NULL DEFAULT 0,
    leidas        integer NOT NULL DEFAULT 0,
    nuevas        integer NOT NULL DEFAULT 0,
    actualizadas  integer NOT NULL DEFAULT 0,
    sin_cambios   integer NOT NULL DEFAULT 0,
    anuladas      integer NOT NULL DEFAULT 0,
    ilegibles     integer NOT NULL DEFAULT 0,
    error         text
);
CREATE INDEX ingesta_fin ON ingesta (fin DESC);
