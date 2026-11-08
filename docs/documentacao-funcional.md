# Documentação funcional do SIMPI

## Estado do código documentado

O código de referência inclui a entrega de consultas e Swagger,
commit `4beaffb`, e a evolução local de criação HTTP e seed de desenvolvimento.
O quadro descreve esse checkout, sem afirmar
integração à main, situação atual do remoto, aprovação de CI ou disponibilidade
em deploy. Para esses estados é necessária uma verificação específica.

| Funcionalidade | Services e infraestrutura | Endpoints HTTP no código documentado |
| --- | --- | --- |
| Autenticação JWT e identidade | Implementados; Usuario é o principal autenticado | POST /api/auth/login e GET /api/auth/me |
| Seletores de cadastros | Consultas implementadas | GET /api/setores, /api/localizacoes e /api/usuarios |
| Bens e transferências | Pesquisa, detalhe e histórico implementados | GET /api/bens, /api/bens/{id} e /api/bens/{id}/transferencias |
| Consultas de solicitações | Pesquisa, visões, paginação e detalhe implementados | GET /api/solicitacoes e /api/solicitacoes/{id} |
| Criação de solicitações | Service implementado; COMUM e ADMINISTRADOR seguem as duas etapas de aprovação | POST /api/solicitacoes, com 201 e Location |
| Aprovação, recusa, cancelamento, envio e recebimento | Implementados nos services | Controllers dessas ações permanecem pendentes |
| Cadastros de demonstração | Seed optativo no perfil seed-desenvolvimento; senha externa e BCrypt | Sem cadastro público ou rota de seed |
| Erros | Advice MVC/negócio e handlers de segurança implementados | Envelope e códigos do contrato |
| Ferramentas | springdoc 3.2.0 e coleção Insomnia | Swagger UI e documento OpenAPI disponíveis quando a aplicação desse checkout estiver iniciada |

A persistência inclui migrations V1 e V2; Hibernate apenas valida o esquema.
Não há cadastro público ou conta de demonstração habilitada por padrão.
O [seed optativo](seed-desenvolvimento.md) prepara cadastros fictícios em banco
local de desenvolvimento, sem solicitações e sem alterar as migrations.
O contrato lista também operações futuras: constar no catálogo não comprova
que o respectivo Controller esteja implementado.

A [execução local, configuração JWT, ferramentas e preparação dos dados](docker-backend.md)
fica centralizada no guia operacional. O
[contrato v1.1](contratos-api-v1.1.md) permanece a referência funcional aprovada.

## Regras preservadas na evolução

Criação, aprovação, recusa, cancelamento, envio, recebimento e consultas usam os
services existentes. Os Controllers de consulta recebem
`@AuthenticationPrincipal Usuario ator`; não recebem identidade por query,
corpo ou header arbitrário. Os services recarregam e revalidam o usuário no banco.
Isso preserva a responsabilidade dos services mesmo com autenticação JWT na entrada.

As consultas de seleção elegem setores/localizações ativos e usuários ativos do
setor informado, mesmo quando esse setor está inativo. Filtros de bens aceitam
referências existentes inativas, sem flexibilizar a criação. Pedidos ocultos não
são expostos e continuam bloqueando a disponibilidade do bem.

Envio e recebimento exigem ator ativo do setor histórico correspondente e usam
as referências preservadas no pedido, sem bloqueios por alterações ou inativações
posteriores dessas referências. Somente o recebimento altera setor, localização
e responsável do bem, sem reativá-lo ou mudar seus demais atributos. Alterações
cadastrais externas podem produzir atribuição incompatível com a situação atual
do responsável; a política de revalidação e correção permanece uma evolução
posterior, antes de disponibilizar manutenção cadastral.

A fila `AGUARDANDO_MINHA_APROVACAO` exige pertencimento atual ao setor da etapa,
sem reatribuir o aprovador registrado. Perder essa elegibilidade não impede
consultar o pedido por outras visões quando houver acesso.

O histórico de transferências consulta somente pedidos `CONCLUIDA`, com origem,
destino e recebimento preservados. Visibilidade pelos três setores históricos e
acesso global administrativo são aplicados antes da contagem/paginação.
Bens inativos continuam consultáveis. Bem inexistente retorna
`RECURSO_NAO_ENCONTRADO`; ausência de transferências visíveis retorna página vazia.
Página além do fim mantém os totais visíveis; a ordem é por recebimento e ID
decrescentes. Nomes vêm das referências cadastradas, sem snapshots ou tabela
adicional de histórico.

## Tratamento de erros HTTP

O advice traduz erros MVC e de negócio para `codigo`, `mensagem` e `erros`,
sem detalhes técnicos. Mapas de falhas por campo viram uma lista ordenada.
`GESTOR_DESIGNADO_INVALIDO` usa `500 / ERRO_INTERNO` com lista vazia;
solicitante inválido ou inativo usa `401 / NAO_AUTENTICADO`, sem revelar a condição
interna. As demais traduções estão no
[acordo aprovado](contratos-api-v1.1.md#convenções-de-tradução-http-aprovadas--05112026).

A integração prevista no acordo de 05/11/2026 já existe no código: filtros e
handlers usam os mesmos DTOs de erro. O fallback do advice preserva exceções
de autenticação e acesso negado, inclusive quando encapsuladas, para a cadeia
responsável, sem convertê-las em 500 nem duplicar respostas.
Falha de login usa CREDENCIAIS_INVALIDAS; falha de autenticação nas rotas
protegidas usa NAO_AUTENTICADO.

404 de rota, 405, 406 e 415 preservam o tratamento padrão do Spring e seus headers.
A definição de seus corpos permanece pendente; esta atualização não resolve
essa decisão nem altera os contratos. A inclusão opcional de
solicitacaoExistenteId continua omitida na implementação documentada.

## Evidências de validação

Os resultados abaixo são registros de execuções anteriores, não garantia de
sucesso de outro checkout, do ambiente local ou de um deploy. Uma execução manual
do comando Maven continua executando testes automatizados; ela não equivale a
validar a API manualmente pelo Swagger.

| Evidência histórica | Escopo e limitação |
| --- | --- |
| Fundação e evoluções anteriores | Foram informadas contagens de 20, 38, 123, 144, 157 e 167 testes em entregas distintas. Não somar essas contagens como se fossem uma suíte única. As decisões e registros históricos estão no documento do modelo. |
| Tratamento de erros, 05/11/2026 | Registro anterior: 43 testes MVC e 3 unitários no Surefire, separados dos 167 testes de integração informados para verify -Pintegration. |
| Controllers de consulta da entrega 4beaffb | 57 casos MVC aprovados nas execuções da entrega, com services mockados e cadeia real de segurança. |
| OpenAPI da entrega 4beaffb | 13 casos aprovados, incluindo segurança por operação, ocultação do principal, parâmetros e schemas. |
| Integração da entrega de Controllers/Swagger | Não executada nessa entrega; resultados históricos de integração não são uma nova execução dela. |
| Criação HTTP e seed, 08/11/2026 — checkout local | 100 testes unitários/MVC/OpenAPI nas seis classes selecionadas e 28 de integração nas classes CriacaoSolicitacaoServiceIT, CriacaoSolicitacaoHttpIT e SeedDesenvolvimentoIT, sem falhas, erros ou testes ignorados. Execuções Maven concluídas com BUILD SUCCESS; não representam toda a suíte nem aprovação de CI. |

Na evolução de criação e seed, os 100 casos selecionados correspondem a
ContratoOpenApiTest (14), SeedDesenvolvimentoTest (10),
SolicitacaoConsultaControllerTest (17), SolicitacaoCriacaoControllerTest (19),
BemConsultaControllerTest (28) e CadastroConsultaControllerTest (12).
Os 10 testes unitários de seed foram repetidos após a organização interna da
carga, sem somar a repetição ao total. A integração cobriu 22 casos do service,
3 de criação/consulta/conflito HTTP com segurança e banco reais e 3 da carga.
Os casos de carga usaram rollback; o perfil público do seed permaneceu
bloqueado em simpi_test. O banco de desenvolvimento não foi populado nesta
validação. O roteiro manual está no [guia do seed](seed-desenvolvimento.md).

### Validação manual de criação e seed — 08/11/2026

Após a validação automatizada, o seed foi executado explicitamente no banco
local simpi_dev. Foram criados 3 setores, 7 usuários, 3 localizações e 6 bens;
os logins das sete contas fictícias foram validados. A senha foi gerada na
execução e entregue em arquivo local fora do repositório, sem incluir tokens
ou credenciais nesta documentação.

No Swagger atualizado, a chamada sem token retornou 401 NAO_AUTENTICADO.
Com a conta ADMINISTRADOR de demonstração, a criação para DEMO-ORIGEM-1
retornou 201 e Location /api/solicitacoes/1. O pedido SOL-2026-0001 iniciou em
AGUARDANDO_GESTOR_AREA, com aprovação pendente para o gestor de DEMO-AREA e
evento CRIACAO. A consulta do detalhe retornou 200; a repetição do POST
retornou 409 BEM_COM_SOLICITACAO_EM_ANDAMENTO. O pedido de demonstração
permaneceu ativo; esta validação não executou aprovação, envio ou recebimento.

Os contextos de consulta e OpenAPI usam `@TestConfiguration` com carregamento
explícito, evitando descoberta automática de seus mocks nos testes de integração.
Há também testes próprios de autenticação e do advice; as contagens 57 e 13
não representam toda a suíte do repositório.

A validação manual relatada das consultas cobriu seletores, páginas vazias,
paginação personalizada e respostas 401, 403 e 404 previstas no contrato.
O bloqueio de dados inválidos pela interface Swagger antes do envio não comprova
HTTP 400 do backend; há cobertura MVC para esses erros. Detalhes e históricos
com sucesso ainda dependem de dados locais adicionais. Esses resultados não
foram reexecutados na atualização documental.

As [instruções de testes](docker-backend.md#testes) distinguem unitários/MVC/OpenAPI,
integração e validação manual. Não registrar credenciais, tokens ou hashes como
evidência. Os registros cronológicos do
[modelo](modelo-solicitacoes-movimentacao.md) descrevem o estado de suas entregas
originais; para o estado do código documentado, use o quadro desta página.

## Documentos

| Documento | Finalidade |
| --- | --- |
| [Entidades e atributos](entidades-e-atributos.md) | Sete entidades, campos, tipos sugeridos, nulabilidade e referências |
| [Enums](enums.md) | Cinco domínios fechados de valores |
| [Regras e fluxos](regras-e-fluxos.md) | Fluxo, permissões, consistência, sessão e testes prioritários |
| [Contratos da API v1.1](contratos-api-v1.1.md) | Rotas, DTOs, validações, respostas e erros |
| [Modelo de solicitações e movimentação](modelo-solicitacoes-movimentacao.md) | Decisões da V2, numeração, UTC, restrições e limites das garantias |
| [DER simplificado](der-simplificado.png) | Apoio visual aos relacionamentos principais |

## Origem e precedência

O contrato v1.1 tem como referência o documento de 27/10/2026. Seus exemplos demonstrativos tiveram nomes e e-mail padronizados, preservando a estrutura e as regras do contrato. As tabelas de entidades e enums correspondem aos documentos aprovados de 23/10/2026. O DER é o desenho conceitual anterior.

As decisões posteriores consolidadas no contrato v1.1 prevalecem sobre as pendências históricas de cancelamento e autoaprovação dos documentos de 23/10. O resumo de fluxos apresenta essas decisões consolidadas. Não adotar o contrato v1.0 como referência de implementação.

Manter português do Brasil nos identificadores definidos pela equipe, preservando campos, rotas e valores já aprovados no contrato. O contrato ainda utiliza, por exemplo, accessToken, status e acoesDisponiveis; não os renomear silenciosamente.

## Decisões técnicas e pendências funcionais

Os itens abaixo registram decisões aprovadas e o que ainda depende de definição;
não são uma lista de trabalho para criar novamente as migrations já aplicadas.

1. **Número da solicitação — implementado:** sequência global sem reinício anual,
   ano UTC de criadaEm, mínimo de quatro dígitos sem truncamento e
   `numero VARCHAR(28) NOT NULL UNIQUE`. O histórico de validação está no
   [documento do modelo](modelo-solicitacoes-movimentacao.md).
2. **Datas em UTC — implementado:** `Instant` e `TIMESTAMP WITH TIME ZONE`; a
   geração do número e a entidade compartilham criadaEm com precisão de microssegundos.
3. **Setor e gestor — implementado na V1:** gestor obrigatório, pertencente ao
   setor gerenciado e com perfil GESTOR.
4. **Domínios fechados — implementado:** `PerfilUsuario` usa `VARCHAR` com CHECK
   e `EnumType.STRING`; os quatro enums da V2 usam a mesma representação, com
   `VARCHAR(40)`, sem enum nativo PostgreSQL.
5. **Concorrência — implementada:** a V2 contém índice único parcial por bem
   ativo e unicidade de aprovação por solicitação/etapa. Criação, decisões e
   demais transições estão nos services, com proteções de estado, concorrência
   e transação. O Controller HTTP de criação está implementado; os das ações permanecem pendentes.
6. **Perfil administrador — abertura aprovada:** ADMINISTRADOR pode abrir
   solicitações seguindo o fluxo de COMUM: gestor do setor do solicitante e,
   depois, gestor do setor de origem do bem. Consulta global não concede
   automaticamente permissão de aprovação ou dispensa de etapas.

Não reabrir decisões implementadas sem nova aprovação. Não ampliar o MVP com
CRUDs, integrações ou recursos não previstos.

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
  coleções inversas. Os repositories foram ampliados pelas consultas posteriores;
  os contratos HTTP retornam DTOs, não entidades persistentes.
- senhaHash permanece privado. Usuario implementa UserDetails e fornece getPassword
  ao Spring Security para validar BCrypt; isso não torna o hash um campo da API.
  Não serializar a entidade nem registrar parâmetros sensíveis. As fixtures são
  sintéticas e não substituem a preparação de uma conta local com hash verificado.
- Hibernate permanece em validate. Flyway é o único responsável por alterar
  o esquema. A V1 foi aplicada com sucesso; a validação manual informada de
  `verify -Pintegration` concluiu 20 testes, sem falhas ou erros, com `BUILD SUCCESS`.

### Preparação do ciclo setor e gestor

A carga inicial deve reservar os IDs pelas sequências e, em uma única
transação SQL, inserir o setor com o ID reservado do gestor e depois o
usuário com esse ID, perfil GESTOR e referência ao próprio setor. A existência
do gestor é verificada no commit. Falta de referência causa falha e rollback.
Não há carga inicial de produção ou seed automático na V1.

Para testar login com perfil COMUM em um banco vazio, é necessário também um
GESTOR de apoio e o setor válido; não atribuir o papel de gestor ao usuário COMUM.
Se existir setor válido, avaliar seu reaproveitamento. A preparação operacional,
incluindo confirmação do destino e BCrypt, está no
[guia de dados locais](docker-backend.md#preparar-dados-locais).

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
