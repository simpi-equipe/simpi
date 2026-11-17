CREATE SEQUENCE sequencia_setor START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE sequencia_usuario START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE sequencia_localizacao START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE sequencia_bem_patrimonial START WITH 1 INCREMENT BY 1;

CREATE TABLE setor (
    id BIGINT DEFAULT nextval('sequencia_setor') CONSTRAINT pk_setor PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL CONSTRAINT uq_setor_codigo UNIQUE,
    nome VARCHAR(150) NOT NULL,
    gestor_id BIGINT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE usuario (
    id BIGINT DEFAULT nextval('sequencia_usuario') CONSTRAINT pk_usuario PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL CONSTRAINT uq_usuario_email UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    setor_id BIGINT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('COMUM', 'GESTOR', 'ADMINISTRADOR')),
    CONSTRAINT fk_usuario_setor FOREIGN KEY (setor_id) REFERENCES setor (id) ON DELETE NO ACTION
);

ALTER TABLE setor ADD CONSTRAINT fk_setor_gestor
    FOREIGN KEY (gestor_id) REFERENCES usuario (id)
    ON DELETE NO ACTION;

CREATE TABLE localizacao (
    id BIGINT DEFAULT nextval('sequencia_localizacao') CONSTRAINT pk_localizacao PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL CONSTRAINT uq_localizacao_codigo UNIQUE,
    nome VARCHAR(150) NOT NULL,
    descricao TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE bem_patrimonial (
    id BIGINT DEFAULT nextval('sequencia_bem_patrimonial') CONSTRAINT pk_bem_patrimonial PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL CONSTRAINT uq_bem_patrimonial_codigo UNIQUE,
    plaqueta VARCHAR(50),
    descricao TEXT NOT NULL,
    setor_id BIGINT NOT NULL,
    localizacao_id BIGINT NOT NULL,
    responsavel_id BIGINT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_bem_patrimonial_setor FOREIGN KEY (setor_id) REFERENCES setor (id) ON DELETE NO ACTION,
    CONSTRAINT fk_bem_patrimonial_localizacao FOREIGN KEY (localizacao_id) REFERENCES localizacao (id) ON DELETE NO ACTION,
    CONSTRAINT fk_bem_patrimonial_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuario (id) ON DELETE NO ACTION
);


CREATE INDEX ix_setor_gestor ON setor (gestor_id);
CREATE INDEX ix_usuario_setor ON usuario (setor_id);
CREATE INDEX ix_bem_patrimonial_setor ON bem_patrimonial (setor_id);
CREATE INDEX ix_bem_patrimonial_localizacao ON bem_patrimonial (localizacao_id);
CREATE INDEX ix_bem_patrimonial_responsavel ON bem_patrimonial (responsavel_id);
