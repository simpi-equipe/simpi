CREATE SEQUENCE sequencia_solicitacao START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE sequencia_aprovacao START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE sequencia_evento_solicitacao START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE sequencia_numero_solicitacao START WITH 1 INCREMENT BY 1;

CREATE TABLE solicitacao (
    id BIGINT DEFAULT nextval('sequencia_solicitacao') CONSTRAINT pk_solicitacao PRIMARY KEY,
    numero VARCHAR(28) NOT NULL CONSTRAINT uq_solicitacao_numero UNIQUE,
    bem_id BIGINT NOT NULL REFERENCES bem_patrimonial (id) ON DELETE NO ACTION,
    solicitante_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE NO ACTION,
    setor_solicitante_id BIGINT NOT NULL REFERENCES setor (id) ON DELETE NO ACTION,
    setor_origem_id BIGINT NOT NULL REFERENCES setor (id) ON DELETE NO ACTION,
    localizacao_origem_id BIGINT NOT NULL REFERENCES localizacao (id) ON DELETE NO ACTION,
    responsavel_origem_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE NO ACTION,
    setor_destino_id BIGINT NOT NULL REFERENCES setor (id) ON DELETE NO ACTION,
    localizacao_destino_id BIGINT NOT NULL REFERENCES localizacao (id) ON DELETE NO ACTION,
    responsavel_destino_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE NO ACTION,
    justificativa TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    enviada_em TIMESTAMP WITH TIME ZONE,
    enviada_por_id BIGINT REFERENCES usuario (id) ON DELETE NO ACTION,
    recebida_em TIMESTAMP WITH TIME ZONE,
    recebida_por_id BIGINT REFERENCES usuario (id) ON DELETE NO ACTION,
    CONSTRAINT ck_solicitacao_status CHECK (status IN (
        'AGUARDANDO_GESTOR_AREA', 'AGUARDANDO_GESTOR_RESPONSAVEL', 'APROVADA',
        'EM_TRANSITO', 'CONCLUIDA', 'RECUSADA', 'CANCELADA')),
    CONSTRAINT ck_solicitacao_envio CHECK ((enviada_em IS NULL) = (enviada_por_id IS NULL)),
    CONSTRAINT ck_solicitacao_recebimento CHECK ((recebida_em IS NULL) = (recebida_por_id IS NULL))
);


CREATE TABLE aprovacao (
    id BIGINT DEFAULT nextval('sequencia_aprovacao') CONSTRAINT pk_aprovacao PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao (id) ON DELETE NO ACTION,
    etapa VARCHAR(40) NOT NULL,
    aprovador_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE NO ACTION,
    decisao VARCHAR(40) NOT NULL DEFAULT 'PENDENTE',
    justificativa TEXT,
    decidida_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_aprovacao_solicitacao_etapa UNIQUE (solicitacao_id, etapa),
    CONSTRAINT ck_aprovacao_etapa CHECK (etapa IN ('GESTOR_AREA', 'GESTOR_RESPONSAVEL')),
    CONSTRAINT ck_aprovacao_decisao CHECK (decisao IN ('PENDENTE', 'APROVADA', 'RECUSADA')),
    CONSTRAINT ck_aprovacao_recusa CHECK (
        decisao <> 'RECUSADA' OR (justificativa IS NOT NULL AND LENGTH(TRIM(justificativa)) > 0)),
    CONSTRAINT ck_aprovacao_data_decisao CHECK (
        (decisao = 'PENDENTE' AND decidida_em IS NULL)
        OR (decisao IN ('APROVADA', 'RECUSADA') AND decidida_em IS NOT NULL))
);

CREATE TABLE evento_solicitacao (
    id BIGINT DEFAULT nextval('sequencia_evento_solicitacao') CONSTRAINT pk_evento_solicitacao PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL REFERENCES solicitacao (id) ON DELETE NO ACTION,
    autor_id BIGINT NOT NULL REFERENCES usuario (id) ON DELETE NO ACTION,
    tipo VARCHAR(40) NOT NULL,
    status_anterior VARCHAR(40),
    status_novo VARCHAR(40) NOT NULL,
    descricao TEXT,
    ocorrido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_evento_solicitacao_tipo CHECK (tipo IN (
        'CRIACAO', 'APROVACAO', 'RECUSA', 'ENVIO', 'RECEBIMENTO', 'CANCELAMENTO')),
    CONSTRAINT ck_evento_solicitacao_status_anterior CHECK (status_anterior IN (
        'AGUARDANDO_GESTOR_AREA', 'AGUARDANDO_GESTOR_RESPONSAVEL', 'APROVADA',
        'EM_TRANSITO', 'CONCLUIDA', 'RECUSADA', 'CANCELADA')),
    CONSTRAINT ck_evento_solicitacao_status_novo CHECK (status_novo IN (
        'AGUARDANDO_GESTOR_AREA', 'AGUARDANDO_GESTOR_RESPONSAVEL', 'APROVADA',
        'EM_TRANSITO', 'CONCLUIDA', 'RECUSADA', 'CANCELADA'))
);


CREATE INDEX ix_solicitacao_bem ON solicitacao (bem_id);
CREATE INDEX ix_solicitacao_solicitante ON solicitacao (solicitante_id);
CREATE INDEX ix_solicitacao_setor_solicitante ON solicitacao (setor_solicitante_id);
CREATE INDEX ix_solicitacao_setor_origem ON solicitacao (setor_origem_id);
CREATE INDEX ix_solicitacao_localizacao_origem ON solicitacao (localizacao_origem_id);
CREATE INDEX ix_solicitacao_responsavel_origem ON solicitacao (responsavel_origem_id);
CREATE INDEX ix_solicitacao_setor_destino ON solicitacao (setor_destino_id);
CREATE INDEX ix_solicitacao_localizacao_destino ON solicitacao (localizacao_destino_id);
CREATE INDEX ix_solicitacao_responsavel_destino ON solicitacao (responsavel_destino_id);
CREATE INDEX ix_solicitacao_enviada_por ON solicitacao (enviada_por_id);
CREATE INDEX ix_solicitacao_recebida_por ON solicitacao (recebida_por_id);
CREATE INDEX ix_aprovacao_aprovador ON aprovacao (aprovador_id);
CREATE INDEX ix_evento_solicitacao_ordem ON evento_solicitacao (solicitacao_id, ocorrido_em, id);
CREATE INDEX ix_evento_solicitacao_autor ON evento_solicitacao (autor_id);
