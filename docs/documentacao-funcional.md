# Documentação funcional do SIMPI

Documentação consolidada em 02/11/2026. A fundação de quatro entidades possui
implementação e migration V1 aplicada no PostgreSQL de integração. A validação
manual informada de `verify -Pintegration` concluiu 20 testes, sem falhas ou
erros, com `BUILD SUCCESS`.

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
3. **Setor e gestor:** decisão implementada na V1, descrita abaixo. O gestor é obrigatório, pertence ao setor gerenciado e possui perfil GESTOR; as duas últimas condições foram omitidas na consolidação e restauradas por confirmação do responsável.
4. **Domínios fechados:** PerfilUsuario utiliza VARCHAR com CHECK e EnumType.STRING, conforme decisão abaixo. A representação dos demais enums permanece pendente. ENUM no dicionário não impõe o uso de tipos enum nativos do PostgreSQL.
5. **Concorrência:** definir a implementação do bloqueio de solicitação ativa por bem e de decisões repetidas, incluindo constraints e transações. Essas garantias já são requisitos, embora o mecanismo ainda não esteja implementado.
6. **Perfil administrador:** abertura de solicitações não definida. Consulta global não concede automaticamente permissões operacionais.

Esses pontos distinguem lacunas técnicas de regras já aprovadas. Apresentar as escolhas para validação antes de implementar os objetos afetados. Não ampliar o MVP com CRUDs, integrações ou recursos não previstos.

## Decisões técnicas da fundação patrimonial

- A V1 cria somente setor, usuario, localizacao e bem_patrimonial, usando os
  tipos e tamanhos do dicionário. IDs BIGINT têm sequências explícitas,
  compartilhadas com JPA, com allocationSize = 1. Campos ativo têm padrão TRUE.
- Somente localizacao.descricao e bem_patrimonial.plaqueta são opcionais.
  Os três códigos e usuario.email são únicos. A unicidade textual usa a
  comparação padrão do PostgreSQL, sem normalização adicional de caixa.
  Plaqueta não é única. Localização não possui vínculo com setor.
- Todas as FKs têm índice e ON DELETE NO ACTION, sem cascatas. A FK
  setor.gestor_id é DEFERRABLE INITIALLY DEFERRED; o campo continua NOT NULL.
  As demais FKs são imediatas. Não há UNIQUE em gestor_id.
- usuario.perfil é VARCHAR(20) NOT NULL com CHECK para COMUM, GESTOR e
  ADMINISTRADOR; o enum Java usa EnumType.STRING. Não há enum nativo ou
  dependência adicional. Novos perfis exigem migration e alteração Java.
- No banco, FK garante que o gestor existe, mas não que possui perfil GESTOR
  ou pertence ao setor gerenciado. Setor.designarGestor valida ambas as
  condições antes de atribuir um usuário a um setor já persistido. Não há
  setters de perfil ou setor em Usuario. Escrita SQL direta pode violar essas
  condições: as fixtures devem respeitá-las. Não foram introduzidos triggers
  ou fluxos de inativação, troca de perfil e cadastro administrativo.
- Quando o setor do solicitante e o setor de origem forem o mesmo, seu
  gestor pode decidir as duas etapas, mediante decisões distintas. Essa regra
  não exige unicidade em gestor_id nem tabelas de solicitações nesta entrega.
- Os pacotes setor, usuario, localizacao e bem agrupam entidades e repositories.
  Associações JPA são unidirecionais, obrigatórias e LAZY, sem cascatas ou
  coleções inversas. Cada repository possui apenas JpaRepository.
- senhaHash é privado, sem getter ou toString que o exponha. Não há logs de
  parâmetros SQL, serialização de entidades ou respostas HTTP nesta entrega.
  Hashes sintéticos nas fixtures têm formato BCrypt; não há autenticação.
- Hibernate permanece em validate. Flyway é o único responsável por alterar
  o esquema. A V1 foi aplicada com sucesso; a validação manual informada de
  `verify -Pintegration` concluiu 20 testes, sem falhas ou erros, com `BUILD SUCCESS`.

### Preparação do ciclo setor e gestor

A carga inicial deve reservar os IDs pelas sequências e, em uma única
transação SQL, inserir o setor com o ID reservado do gestor e depois o
usuário com esse ID, perfil GESTOR e referência ao próprio setor. A existência
do gestor é verificada no commit. Falta de referência causa falha e rollback.
Não há carga inicial de produção ou seed automático na V1.

As fixtures executam essa sequência por JDBC na transação de teste. O JPA
carrega as referências já persistidas. A FK adiável não controla a análise de
dependências transitórias do Hibernate: não se deve presumir que save,
cascatas ou ordenação de flush persistirão duas entidades novas com esse ciclo.
Setor tem apenas construtor protegido para JPA; a preparação inicial não é
oferecida como API Java de cadastro. Não há infraestrutura genérica de carga.

### Proteção e verificação de integração

Todos os testes que inicializam Spring registram ProtecaoBancoIntegracao em
ContextConfiguration. O ApplicationContextInitializer resolve a URL no
ambiente Spring antes de carregar beans, DataSource e Flyway. Aceita somente
o formato jdbc:postgresql://HOST[:PORTA]/simpi_test, com HOST exatamente
localhost, 127.0.0.1 ou [::1]. Porta opcional de 1 a 65535, sem zeros iniciais.
Parâmetros, fragmentos, credenciais na URL, múltiplos hosts, caminhos
codificados e demais formatos são rejeitados. Configurações alternativas de
URL Flyway, Hikari ou JNDI também são rejeitadas. Falhas usam mensagem fixa,
sem URL, credenciais ou causa original. Uma execução remota futura exige
revisão explícita dessa proteção. Os endereços local e CI existentes são aceitos.

Os testes de dados usam transações com rollback e antecipam a validação da
FK adiável quando necessário. O teste positivo de commit mantém um único
cenário sintético completo em simpi_test, sem rotina de exclusão. O teste de
commit inválido verifica a falha real no fechamento e a ausência dos registros
após rollback. Sequências podem avançar mesmo em cenários revertidos.
Os testes da proteção usam contextos sem refresh e não abrem conexão.
