-- Alertas de los usuarios (búsquedas guardadas), los avisos que generan y la bandeja de salida
-- del correo. Los usuarios viven en Keycloak: aquí solo se guarda su identificador (claim «sub»)
-- y el correo al que quieren los avisos.

CREATE TABLE alerta (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       text NOT NULL,
    correo           text NOT NULL,
    nombre           text NOT NULL,
    texto            text,
    tipos_contrato   text[] NOT NULL DEFAULT '{}',
    procedimientos   text[] NOT NULL DEFAULT '{}',
    nuts             text[] NOT NULL DEFAULT '{}',
    cpv              text[] NOT NULL DEFAULT '{}',
    importe_minimo   numeric(18, 2),
    importe_maximo   numeric(18, 2),
    solo_fondos_ue   boolean NOT NULL DEFAULT false,
    por_correo       boolean NOT NULL DEFAULT true,
    activa           boolean NOT NULL DEFAULT true,
    creada_en        timestamptz NOT NULL,
    actualizada_en   timestamptz NOT NULL,
    version          integer NOT NULL DEFAULT 0
);
CREATE INDEX alerta_usuario ON alerta (usuario_id);

-- Un aviso por alerta y licitación, nunca dos: la restricción única es lo que garantiza que una
-- reingesta o un evento reintentado no avisan dos veces de lo mismo.
CREATE TABLE aviso (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alerta_id      bigint NOT NULL REFERENCES alerta (id) ON DELETE CASCADE,
    licitacion_id  bigint NOT NULL REFERENCES licitacion (id),
    ingesta_id     bigint NOT NULL,
    creado_en      timestamptz NOT NULL,
    leido_en       timestamptz,
    UNIQUE (alerta_id, licitacion_id)
);
CREATE INDEX aviso_alerta_creado ON aviso (alerta_id, creado_en DESC);

-- Bandeja de salida: el correo se guarda en la misma transacción que lo origina y un proceso
-- aparte lo envía con reintentos. Si el servidor de correo falla, no se pierde nada.
CREATE TABLE correo_saliente (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    destinatario     text NOT NULL,
    asunto           text NOT NULL,
    texto            text NOT NULL,
    html             text NOT NULL,
    creado_en        timestamptz NOT NULL,
    proximo_intento  timestamptz NOT NULL,
    intentos         integer NOT NULL DEFAULT 0,
    enviado_en       timestamptz,
    ultimo_error     text
);
CREATE INDEX correo_saliente_pendiente ON correo_saliente (proximo_intento) WHERE enviado_en IS NULL;
