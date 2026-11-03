# Entidades e atributos do SIMPI

Transcrição das tabelas do dicionário técnico de 23/10/2026, recuperadas do arquivo utilizado para gerar o documento aprovado. Tipos e tamanhos são sugestões; não representam migrations já implementadas.

As decisões de fluxo posteriores estão em [Regras e fluxos](regras-e-fluxos.md) e no [contrato da API v1.1](contratos-api-v1.1.md). Consulte também as [pendências de implementação](documentacao-funcional.md#pendencias-antes-das-migrations).

PK: chave primária. FK: chave estrangeira. UNIQUE: valor único. A coluna Nulo informa se o atributo aceita ausência de valor. ENUM representa um domínio fechado de valores; sua representação no PostgreSQL ainda será definida.

## 1 Setor — setor

| Atributo | Tipo sugerido | Nulo | Chave / referência / restrição |
| --- | --- | --- | --- |
| id | BIGINT | Não | PK; gerado automaticamente |
| codigo | VARCHAR(30) | Não | UNIQUE |
| nome | VARCHAR(150) | Não | — |
| gestor_id | BIGINT | Não | FK → usuario.id |
| ativo | BOOLEAN | Não | Padrão: TRUE |

## 2 Usuário — usuario

| Atributo | Tipo sugerido | Nulo | Chave / referência / restrição |
| --- | --- | --- | --- |
| id | BIGINT | Não | PK; gerado automaticamente |
| nome | VARCHAR(150) | Não | — |
| email | VARCHAR(254) | Não | UNIQUE |
| senha_hash | VARCHAR(255) | Não | — |
| perfil | ENUM | Não | — |
| setor_id | BIGINT | Não | FK → setor.id |
| ativo | BOOLEAN | Não | Padrão: TRUE |

perfil: COMUM, GESTOR, ADMINISTRADOR.

## 3 Localização — localizacao

| Atributo | Tipo sugerido | Nulo | Chave / referência / restrição |
| --- | --- | --- | --- |
| id | BIGINT | Não | PK; gerado automaticamente |
| codigo | VARCHAR(30) | Não | UNIQUE |
| nome | VARCHAR(150) | Não | — |
| descricao | TEXT | Sim | — |
| ativo | BOOLEAN | Não | Padrão: TRUE |

## 4 Bem patrimonial — bem_patrimonial

| Atributo | Tipo sugerido | Nulo | Chave / referência / restrição |
| --- | --- | --- | --- |
| id | BIGINT | Não | PK; gerado automaticamente |
| codigo | VARCHAR(30) | Não | UNIQUE; código do protótipo |
| plaqueta | VARCHAR(50) | Sim | Sem restrição UNIQUE |
| descricao | TEXT | Não | — |
| setor_id | BIGINT | Não | FK → setor.id |
| localizacao_id | BIGINT | Não | FK → localizacao.id |
| responsavel_id | BIGINT | Não | FK → usuario.id |
| ativo | BOOLEAN | Não | Padrão: TRUE |

## 5 Solicitação — solicitacao

| Atributo | Tipo sugerido | Nulo | Chave / referência / restrição |
| --- | --- | --- | --- |
| id | BIGINT | Não | PK; gerado automaticamente |
| bem_id | BIGINT | Não | FK → bem_patrimonial.id |
| solicitante_id | BIGINT | Não | FK → usuario.id |
| setor_solicitante_id | BIGINT | Não | FK → setor.id |
| setor_origem_id | BIGINT | Não | FK → setor.id |
| localizacao_origem_id | BIGINT | Não | FK → localizacao.id |
| responsavel_origem_id | BIGINT | Não | FK → usuario.id |
| setor_destino_id | BIGINT | Não | FK → setor.id |
| localizacao_destino_id | BIGINT | Não | FK → localizacao.id |
| responsavel_destino_id | BIGINT | Não | FK → usuario.id |
| justificativa | TEXT | Não | — |
| status | ENUM | Não | — |
| criada_em | TIMESTAMP | Não | — |
| enviada_em | TIMESTAMP | Sim | — |
| enviada_por_id | BIGINT | Sim | FK → usuario.id |
| recebida_em | TIMESTAMP | Sim | — |
| recebida_por_id | BIGINT | Sim | FK → usuario.id |

status: AGUARDANDO_GESTOR_AREA, AGUARDANDO_GESTOR_RESPONSAVEL, APROVADA, EM_TRANSITO, CONCLUIDA, RECUSADA, CANCELADA.

## 6 Aprovação — aprovacao

| Atributo | Tipo sugerido | Nulo | Chave / referência / restrição |
| --- | --- | --- | --- |
| id | BIGINT | Não | PK; gerado automaticamente |
| solicitacao_id | BIGINT | Não | FK → solicitacao.id |
| etapa | ENUM | Não | — |
| aprovador_id | BIGINT | Não | FK → usuario.id |
| decisao | ENUM | Não | Padrão: PENDENTE |
| justificativa | TEXT | Sim | Obrigatória se decisao = RECUSADA |
| decidida_em | TIMESTAMP | Sim | Preenchida ao aprovar ou recusar |

etapa: GESTOR_AREA, GESTOR_RESPONSAVEL. decisao: PENDENTE, APROVADA, RECUSADA. Chave única composta: UNIQUE (solicitacao_id, etapa).

## 7 Evento da solicitação — evento_solicitacao

| Atributo | Tipo sugerido | Nulo | Chave / referência / restrição |
| --- | --- | --- | --- |
| id | BIGINT | Não | PK; gerado automaticamente |
| solicitacao_id | BIGINT | Não | FK → solicitacao.id |
| autor_id | BIGINT | Não | FK → usuario.id |
| tipo | ENUM | Não | — |
| status_anterior | ENUM | Sim | Mesmo domínio de solicitacao.status |
| status_novo | ENUM | Não | Mesmo domínio de solicitacao.status |
| descricao | TEXT | Sim | — |
| ocorrido_em | TIMESTAMP | Não | — |

tipo: CRIACAO, APROVACAO, RECUSA, ENVIO, RECEBIMENTO, CANCELAMENTO.
