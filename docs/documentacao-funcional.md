# Documentação funcional do SIMPI

Documentação consolidada em 02/11/2026. Não contém implementação nem migrations.

## Documentos

| Documento | Finalidade |
| --- | --- |
| [Entidades e atributos](entidades-e-atributos.md) | Sete entidades, campos, tipos sugeridos, nulabilidade e referências |
| [Enums](enums.md) | Cinco domínios fechados de valores |
| [Regras e fluxos](regras-e-fluxos.md) | Fluxo, permissões, consistência, sessão e testes prioritários |
| [Contratos da API v1.1](contratos-api-v1.1.md) | Rotas, DTOs, validações, respostas e erros |
| [DER simplificado](der-simplificado.png) | Apoio visual aos relacionamentos principais |

## Origem e precedência

O contrato v1.1 tem como referência o documento de 27/10/2026. Seus exemplos demonstrativos tiveram nomes e e-mail padronizados, preservando a estrutura e as regras do contrato. As tabelas de entidades e enums correspondem aos documentos aprovados de 23/10/2026. O DER é o desenho conceitual anterior.

As decisões posteriores consolidadas no contrato v1.1 prevalecem sobre as pendências históricas de cancelamento e autoaprovação dos documentos de 23/10. O resumo de fluxos apresenta essas decisões consolidadas. Não adotar o contrato v1.0 como referência de implementação.

Manter português do Brasil nos identificadores definidos pela equipe, preservando campos, rotas e valores já aprovados no contrato. O contrato ainda utiliza, por exemplo, accessToken, status e acoesDisponiveis; não os renomear silenciosamente.

## Pendencias antes das migrations

1. **Número da solicitação:** o contrato possui numero gerado pelo backend, mas o dicionário técnico antigo não contém esse atributo. Propor geração, armazenamento, unicidade e tamanho antes de criar a tabela solicitacao.
2. **Datas em UTC:** o dicionário sugere TIMESTAMP genericamente; definir o tipo Java e o tipo PostgreSQL que implementarão corretamente o contrato em UTC.
3. **Setor e gestor:** gestor_id é obrigatório no modelo aprovado. Resolver a referência circular e a carga inicial de setor/usuario sem afrouxar essa regra silenciosamente.
4. **Domínios fechados:** decidir como persistir os enums e validar os valores no banco. ENUM no dicionário não impõe o uso de tipos enum nativos do PostgreSQL.
5. **Concorrência:** definir a implementação do bloqueio de solicitação ativa por bem e de decisões repetidas, incluindo constraints e transações. Essas garantias já são requisitos, embora o mecanismo ainda não esteja implementado.
6. **Perfil administrador:** abertura de solicitações não definida. Consulta global não concede automaticamente permissões operacionais.

Esses pontos distinguem lacunas técnicas de regras já aprovadas. Apresentar as escolhas para validação antes de implementar os objetos afetados. Não ampliar o MVP com CRUDs, integrações ou recursos não previstos.
