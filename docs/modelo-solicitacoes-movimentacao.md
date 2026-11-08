# Modelo de solicitações e movimentação

As seções de evolução e revisão registram o estado das respectivas entregas na
época, não uma afirmação permanente sobre o checkout ou a main. O
[estado do código documentado](documentacao-funcional.md#estado-do-código-documentado)
separa services, endpoints HTTP e pendências. As
[instruções operacionais](docker-backend.md) são mantidas em uma única fonte.

Decisões técnicas aprovadas para a evolução da fundação patrimonial. Referências:
[dicionário](entidades-e-atributos.md), [enums](enums.md),
[regras](regras-e-fluxos.md) e [API v1.1](contratos-api-v1.1.md).

## Escopo e migração

Na entrega de modelo da V2 registrada nesta seção, foram acrescentadas
`solicitacao`, `aprovacao` e `evento_solicitacao`, sem modificar a V1 ou o
contrato HTTP. Flyway continua responsável pelo esquema e Hibernate somente
valida. Naquela etapa não havia endpoints, autenticação, DTOs HTTP ou execução
de ações do fluxo; as entregas posteriores estão registradas cronologicamente
abaixo. Os repositories possuem somente `JpaRepository`.

A funcionalidade fica em `br.edu.simpi.solicitacao`, dividida em `model`
(Solicitacao, Aprovacao, EventoSolicitacao e NumeroSolicitacao), `enums`
(os quatro domínios fechados), `repository` (os três repositories) e `service`
(NumeroSolicitacaoService). Os demais módulos mantêm sua organização atual.
Pacotes técnicos em inglês seguem a exceção explícita de idioma do AGENTS.md;
domínio, métodos, atributos e variáveis definidos pela equipe permanecem em português.

O construtor público de `NumeroSolicitacao` que recebe o sequencial permite
ao serviço construir o valor sem expor o método interno de formatação.
Esse método mantém visibilidade de pacote; o teste puro reside em
`br.edu.simpi.solicitacao.model`. Construtores JPA continuam protegidos e
campos das entidades continuam privados.

Os IDs utilizam sequências explícitas com `allocationSize = 1`. As associações
são unidirecionais, LAZY e sem cascatas. As FKs são imediatas, usam
`ON DELETE NO ACTION` e possuem índices, próprios ou com a FK na primeira
coluna de índice composto. A fundação e sua FK adiável permanecem intactas.

Os quatro novos enums usam `VARCHAR(40)` com `CHECK` e `EnumType.STRING`.
Os valores são exatamente os aprovados. Alterações de domínio exigem nova
migration e atualização Java.

## Número e instantes

`sequencia_numero_solicitacao` é global, crescente, sem ciclo e sem reinício
anual. `NumeroSolicitacaoService.gerar(criadaEm)` obtém um valor com `nextval`,
sem contagem de registros, e produz `SOL-AAAA-NNNN`: ano UTC do instante
recebido, sequencial com no mínimo quatro dígitos, sem truncamento acima de
9999. Não há promessa de sequência sem lacunas: rollback também consome valores.
Não reiniciar ou reposicionar a sequência em operação normal.

`numero` é `VARCHAR(28) NOT NULL UNIQUE`, suficiente para prefixo, ano de quatro
dígitos e sequencial positivo BIGINT de até 19 dígitos. IDs e número visual são
independentes. A sequência coordena chamadas concorrentes; UNIQUE impede que um
número duplicado seja persistido, inclusive por SQL direto.

`NumeroSolicitacao` mantém número e `criadaEm` juntos e valida a correspondência
do ano. O construtor de `Solicitacao` recebe esse valor e copia exatamente seu
instante. Antes de derivar o ano, a geração trunca nanossegundos para microssegundos,
evitando arredondamento para o ano seguinte no PostgreSQL na virada do ano.
O futuro caso de uso deverá capturar uma única data de criação no servidor e
fornecê-la ao gerador; não deverá aceitar número ou data do cliente.

Todos os instantes são `Instant` no Java e `TIMESTAMP WITH TIME ZONE` no banco.
PostgreSQL preserva o instante, sem conservar o fuso original. A apresentação
da API segue ISO 8601 em UTC com `Z`; a sessão do banco pode exibir
outro fuso sem mudar o instante. A precisão de persistência é de microssegundos.
SQL direto não recebe garantia de formato ou correspondência entre ano e data:
essas validações pertencem à construção Java, além da unicidade no banco.

## Histórico

A abertura copia as referências de origem do bem e do setor do solicitante;
o destino proposto permanece na solicitação. A consulta histórica deverá usar
esses campos, nunca o cadastro atual do bem ou o setor atual do solicitante.
O aprovador é registrado na criação da etapa e não é recalculado na consulta.
Nomes dos cadastros não são congelados, conforme o contrato.

Não há setters genéricos. Identidade da solicitação, número, criação, referências
históricas, justificativa inicial, designação da aprovação e campos dos eventos
usam `updatable = false`. Isso impede regravação acidental pelo mapeamento JPA,
mas não protege contra SQL direto, atualizações em massa ou exclusão pelo
repository. Não há triggers de imutabilidade ou política de privilégios nova.
A proibição de editar/excluir eventos deverá ser respeitada pelos casos de uso.

## Garantias do banco

- PKs, FKs, campos obrigatórios, domínios fechados e número único.
- Índice único parcial em `bem_id` nos estados AGUARDANDO_GESTOR_AREA,
  AGUARDANDO_GESTOR_RESPONSAVEL, APROVADA e EM_TRANSITO. Só pode existir uma
  solicitação ativa por bem, mesmo em transações concorrentes. CONCLUIDA,
  RECUSADA e CANCELADA não bloqueiam uma nova solicitação.
- `UNIQUE (solicitacao_id, etapa)`, permitindo o mesmo aprovador em duas
  etapas distintas.
- Recusa exige explicitamente justificativa não nula com ao menos um
  caractere fora da classe de espaços do PostgreSQL, incluindo proteção
  contra texto composto somente de espaços, tabulações ou quebras de linha.
- PENDENTE exige ausência de `decidida_em`; APROVADA e RECUSADA exigem data.
- Cada par `enviada_em/enviada_por_id` e `recebida_em/recebida_por_id` deve estar
  integralmente ausente ou preenchido. Isso não valida sequência temporal,
  estado correspondente ou existência de envio antes do recebimento.

## Responsabilidades implementadas nos services

As regras abaixo orientaram os casos de uso e foram implementadas nos services
descritos nas seções de evolução posteriores. ADMINISTRADOR pode abrir
solicitações pelo fluxo de COMUM, com as duas etapas de aprovação.

- Revalidar usuário ativo, permissões, perfil, setor atual e aprovador designado.
  ADMINISTRADOR inicia em GESTOR_AREA, pelo gestor de seu setor, e segue para
  GESTOR_RESPONSAVEL, pelo gestor do setor de origem do bem.
- Validar bem e destino ativos, responsável pertencente ao destino, justificativa
  não vazia e diferença entre origem e destino. Localização independe do setor.
- Bloquear o bem e revalidar disponibilidade e origem ao abrir a solicitação.
  Criar solicitação, primeira aprovação e evento em uma única transação.
- Selecionar etapa inicial para COMUM ou GESTOR, sem aprovação fictícia;
  criar etapa posterior somente quando cabível.
- Serializar transições no registro da solicitação e revalidar estado e decisão
  da aprovação identificada dentro da mesma transação. A unicidade da etapa não
  impede, isoladamente, decisões repetidas ou eventos duplicados.
- Impedir envio e cancelamento simultâneos, reabertura de estados finais e
  cancelamento após envio. Exigir motivo para cancelar.
- Atualizar o bem somente no recebimento, junto da conclusão e do evento, com
  rollback integral em caso de falha. A pessoa que recebe não substitui o responsável.
- Preservar consulta por setores históricos e mapear conflitos para os códigos
  existentes na API v1.1, sem expor pedido inacessível ao usuário.

Não há triggers de transição ou deduplicação automática de eventos. As operações
e a revalidação do ator ficam nos services; constraints por linha não garantem,
sozinhas, atomicidade entre tabelas.

## Validação manual e pendências

Os testes cobrem persistência/releitura JPA, enums, UTC em sessão de outro fuso,
virada do ano, referências históricas, proteção de atualização JPA, obrigatoriedade,
FKs, domínios, recusa, pares de atores/datas, unicidades e estados ativos/finais.
Há cenário de rollback do conjunto e geração sem reutilização após rollback.
O teste de formatação acima de 9999 é puro: não executa `setval`, reinício ou
avanço artificial de sequências compartilhadas.

Os testes concorrentes usam transações REQUIRES_NEW em threads independentes,
barreira com limite de 4 segundos, lock_timeout de 3 segundos, statement_timeout
de 5 segundos, transações de 8 segundos, espera de Future de 10 segundos e
limite de teste de 30 segundos. O executor é interrompido e sua finalização
é aguardada por até 5 segundos. O cenário de disputa confirma um vencedor,
um conflito de unicidade e ausência de aprovações/eventos parciais.

Todos os contextos Spring reutilizam `ProtecaoBancoIntegracao`, restrita ao
PostgreSQL local e banco `simpi_test`. Os cenários comuns revertem transações.
Concorrência precisa confirmar fixtures e vencedores visíveis entre conexões;
esses dados sintéticos permanecem no banco de teste, sem limpeza destrutiva.
Falhas também podem deixar fixtures confirmadas. Sequências podem avançar.

Validação manual informada pelo responsável: `verify -Pintegration` concluiu
38 testes, zero falhas, zero erros e `BUILD SUCCESS`. O resultado é registrado
conforme informado, sem nova execução de testes ou build para esta atualização.
A validação corresponde à suíte executada na entrega original do modelo. Naquela
etapa, casos de uso, permissões e transições ainda estavam pendentes; as seções
de evolução abaixo registram sua implementação posterior nos services. A exposição
HTTP de criação, aprovação, recusa e cancelamento já está implementada;
envio e recebimento permanecem pendentes, conforme o quadro atual da
[documentação funcional](documentacao-funcional.md#estado-do-código-documentado).

## Evolução: criação de solicitações no service

`SolicitacaoService.criar` implementa a abertura prevista no contrato v1.1,
com validações em chamadas diretas, solicitante interno recarregado pelo ID,
preservação da origem e do setor solicitante, número e instante definidos no
servidor, aprovação inicial pendente e evento `CRIACAO`. O bloqueio do bem e
a gravação do pedido, aprovação e evento compartilham a mesma transação.
A resposta completa inclui `acoesDisponiveis`, sem executar essas ações.

Validação local manual informada pelo responsável: `verify -Pintegration`
concluiu 57 testes, zero falhas, zero erros e `BUILD SUCCESS`. O resultado
inclui os testes de criação, regras, concorrência e rollback. Não houve nova
execução de testes ou build para este registro.

Controller, exposição de `POST /api/solicitacoes` e integração com autenticação
permanecem pendentes. As demais ações do fluxo não foram implementadas nesta
entrega. O motivo interno `GESTOR_DESIGNADO_INVALIDO` ainda não possui tradução
HTTP definida.

## Evolução: aprovação e recusa de etapas no service

`SolicitacaoService.aprovar` e `recusar` recebem `solicitacaoId`, `aprovacaoId`
e usuário ator por parâmetro interno confiável; a recusa também recebe a
justificativa. O usuário é recarregado pelo ID e revalidado após eventual
espera pelo bloqueio. Conta ativa, perfil GESTOR, setor atual, acesso pelos
setores históricos e identidade do aprovador são verificados antes de expor
estado ou decisão. O aprovador registrado na criação da etapa é preservado:
trocar o gestor cadastrado não substitui uma designação existente.

A aprovação deve pertencer ao pedido, estar pendente e corresponder à etapa
aguardada. Aprovar `GESTOR_AREA` cria `GESTOR_RESPONSAVEL` pendente, designada
ao gestor válido do setor de origem preservado, e avança o pedido para
`AGUARDANDO_GESTOR_RESPONSAVEL`. Aprovar essa segunda etapa leva a `APROVADA`.
Autoaprovação é permitida ao aprovador designado; o mesmo gestor nas duas
etapas precisa de duas decisões explícitas, cada uma com seu `aprovacaoId`.
Pedidos abertos por gestor continuam sem uma etapa de área ficticiamente aprovada.

A recusa exige justificativa não vazia após remoção de espaços externos,
registra a decisão e encerra o pedido como `RECUSADA`, sem criar etapas
posteriores. Esse estado permite nova solicitação pelo índice parcial já
existente. Aprovar e recusar não alteram o cadastro do bem nem as referências
históricas do pedido. Eventos são apenas acrescentados.

Cada operação usa uma transação e bloqueio pessimista exclusivo na
solicitação, com atualização do estado carregado no contexto JPA. O instante
da decisão é capturado uma vez, após o bloqueio e as revalidações, em UTC e
com precisão de microssegundos, compartilhado pela decisão e pelo evento.
Decisão, estado, eventual próxima etapa e evento são persistidos juntos;
falhas de persistência são propagadas imediatamente para rollback, sem
continuar consultando ou gravando na transação comprometida.

Repetir uma etapa decidida produz o motivo `ETAPA_JA_DECIDIDA`; etapa pendente
em estado incompatível produz `ESTADO_INCOMPATIVEL`. Aprovação ausente ou de
outro pedido produz `APROVACAO_NAO_ENCONTRADA`, respeitada a verificação prévia
de acesso. Os motivos permanecem independentes de HTTP. Em particular,
`GESTOR_DESIGNADO_INVALIDO` é interno e sua tradução HTTP continua pendente,
sem novo código público definido nesta entrega.

O `SolicitacaoMapper` reutiliza o DTO completo `SolicitacaoResponse`,
incluindo todas as aprovações na ordem do fluxo e eventos por instante e ID.
`RegrasDecisaoAprovacao` compartilha as condições de autorização, pendência e
estado entre as operações e as ações `APROVAR`/`RECUSAR` anunciadas em
`acoesDisponiveis`. Anunciar as demais ações não implementa sua execução.

Os testes `DecisaoAprovacaoServiceIT` e `ConcorrenciaDecisaoAprovacaoIT` cobrem
sucesso, autoaprovação, decisões explícitas do mesmo gestor, recusa,
permissões atuais, preservação da designação e das referências históricas,
estado, repetição, resposta completa, cadastro do bem inalterado e rollback
após falhas de gravação. As disputas nas duas etapas verificam um vencedor e
um conflito, sem eventos duplicados. Há espera real pelo bloqueio em conexões
independentes para verificar revalidação do ator e captura posterior do instante.

No teste do relógio, a criação já executou `flush`; o contexto é limpo e a
solicitação é recarregada com modo JPA `NONE` antes da decisão. A asserção
de `PESSIMISTIC_WRITE` permanece dentro de `Clock.instant()`, sem aceitar
outros modos. Isso evita herdar o modo interno `WRITE` da inserção na mesma
transação, convertido pelo Hibernate para `PESSIMISTIC_FORCE_INCREMENT` na
API JPA. A asserção verifica o modo registrado antes da captura do instante;
os testes entre conexões complementam a garantia de exclusão efetiva.

Validação manual final informada pelo responsável: **77 testes, zero falhas,
zero erros, zero ignorados e `BUILD SUCCESS`**. O resultado é registrado conforme
informado, sem repetir testes ou builds nesta revisão documental. Os registros
das entregas anteriores permanecem como histórico.

Controllers, endpoints de criação/aprovação/recusa, integração com autenticação
e tradução HTTP de `GESTOR_DESIGNADO_INVALIDO` permanecem pendentes. Cancelamento,
envio, recebimento e consultas ficam fora desta entrega. Migrations, contratos,
dependências e arquivos da autenticação não foram alterados.

## Evolução: cancelamento de solicitações no service

`SolicitacaoService.cancelar(solicitacaoId, motivo, ator)` recebe o ator pela
integração interna confiável e retorna o mesmo `SolicitacaoResponse` completo.
O motivo é obrigatório, rejeita texto vazio ou somente espaços, tabulações e
quebras de linha e tem espaços externos removidos. A justificativa inicial
da solicitação não é substituída pelo motivo do cancelamento.

Definição funcional aprovada para esta entrega: somente o próprio solicitante
ativo pode cancelar e deve manter acesso ao pedido pelas regras de
visibilidade existentes. O setor atual pode coincidir com qualquer setor
participante preservado no pedido (solicitante, origem ou destino); a consulta
global do ADMINISTRADOR também é preservada. A mudança de setor, isoladamente,
não impede o cancelamento. A perda de acesso produz `ACAO_NAO_PERMITIDA` antes
de revelar estado ou decisões. Nenhum perfil concede cancelamento de pedidos
alheios, e não há exigência de permanecer no setor solicitante original.

`RegrasVisibilidadeSolicitacao` reúne a verificação anteriormente existente em
`RegrasDecisaoAprovacao.temAcesso`, que continua delegando à mesma regra.
`RegrasCancelamentoSolicitacao` compartilha os critérios de conta ativa,
visibilidade, autoria e estado entre o service e `CANCELAR` em
`acoesDisponiveis`, sem criar novas permissões por perfil.

Após validar identificador e motivo, o service recarrega o ator, bloqueia a
solicitação com `PESSIMISTIC_WRITE`, atualiza a entidade no contexto JPA e
recarrega novamente o ator após eventual espera. Acesso e autoria precedem a
verificação de estado. São canceláveis `AGUARDANDO_GESTOR_AREA`,
`AGUARDANDO_GESTOR_RESPONSAVEL` e `APROVADA`, desde que não haja envio registrado.
Estado incompatível, inclusive cancelamento repetido, produz
`ESTADO_INCOMPATIVEL`, sem novo evento. Os motivos de falha usam os códigos
já previstos no contrato, sem implementar tradução HTTP.

O instante do servidor é capturado uma vez após o bloqueio e as validações,
em UTC e com precisão de microssegundos. A alteração para `CANCELADA` e o evento
`CANCELAMENTO` com ator, estados anterior/novo e motivo normalizado na
`descricao` compartilham a mesma transação. Falhas de persistência encerram a
operação com rollback integral, sem continuar consultando ou gravando. O bem,
as referências históricas, os eventos anteriores e todas as aprovações são
preservados; etapas pendentes não recebem decisões fictícias.
As aprovações usadas na resposta completa também são atualizadas no contexto
JPA após o `flush`, para refletir decisões confirmadas enquanto o cancelamento
aguardava o lock, mesmo quando essas entidades já estavam carregadas.

O cancelamento compartilha com aprovação e recusa o lock da solicitação e não
adquire lock no bem. A criação existente bloqueia o bem antes de verificar
pedido ativo; assim, não se acrescenta uma aquisição inversa solicitação ->
bem. Após o commit, o estado `CANCELADA` libera a disponibilidade pelo índice
parcial existente, permitindo novo pedido sem alteração cadastral do bem.

Nas disputas, dois cancelamentos confirmam somente um evento. Cancelamento
confirmado primeiro impede aprovação ou recusa posterior; recusa confirmada
primeiro impede cancelamento. Aprovação confirmada primeiro pode ser seguida
de cancelamento válido, pois seu estado resultante continua cancelável: nesse
caso ambas as ações são preservadas em sequência, com suas decisões e eventos.

`CancelamentoSolicitacaoServiceIT` e `ConcorrenciaCancelamentoSolicitacaoIT`
foram escritos para sucesso nos estados permitidos, autoria e conta ativa,
visibilidade por setores históricos e exceção administrativa, mudança para
setor participante e perda de acesso, motivo inválido, estado, repetição,
preservação de dados, resposta completa, captura do instante sob bloqueio,
rollback e novo pedido após commit. Os cenários concorrentes usam conexões
independentes, esperas limitadas e verificação de espera real pelo lock.

Validação manual final informada pelo responsável para a entrega de
cancelamento: **99 testes, zero falhas, zero erros, zero ignorados e
`BUILD SUCCESS`**. O resultado é registrado conforme informado, sem repetir
testes ou builds nesta revisão. O resultado anterior de 77 testes da entrega
de aprovação e recusa permanece registrado na seção correspondente, assim
como os resultados das demais entregas anteriores.

Controllers, endpoints, integração com autenticação e tradução
HTTP de `GESTOR_DESIGNADO_INVALIDO` permanecem pendentes. Envio, recebimento e
consultas continuam fora do escopo; migrations, contratos, dependências e
arquivos de autenticação permanecem intactos.

## Evolução: envio e recebimento no service

`SolicitacaoService.enviar(solicitacaoId, ator)` e `receber(solicitacaoId, ator)`
recebem o usuário pela integração interna confiável e retornam o DTO completo
existente. Não há corpo de entrada, Controller, endpoint ou autenticação nesta
entrega. A conta do ator é recarregada e deve estar ativa. Visibilidade e setor
atual são verificados antes de expor estado, datas ou decisões; qualquer perfil
pode executar se pertencer ao setor histórico exigido. A consulta global do
ADMINISTRADOR não dispensa o vínculo com o setor operacional correspondente.

O envio exige `APROVADA`, sem envio anterior. Registra `enviadaPor` e
`enviadaEm`, muda para `EM_TRANSITO` e acrescenta evento `ENVIO`. Não altera o
cadastro do bem. O recebimento exige `EM_TRANSITO` e envio registrado, sem
recebimento anterior. Registra `recebidaPor` e `recebidaEm`, aplica ao bem as
três referências de destino preservadas, muda para `CONCLUIDA` e acrescenta
evento `RECEBIMENTO`. O recebedor pode ser diferente do responsável de destino.

Definição funcional aprovada para o protótipo: alterações ou inativações
posteriores do bem, setor, localização ou responsável não acrescentam bloqueios
ao envio ou recebimento. Mesmo que o cadastro atual do bem divirja da origem
histórica ou o responsável tenha mudado de setor, aplicar exatamente os IDs
de setor, localização e responsável de destino registrados no pedido. Não
substituir o responsável pelo recebedor, reativar registros ou alterar código,
plaqueta, descrição, situação ativa ou outros atributos do bem. A exigência de
ator ativo do setor histórico continua válida, mesmo quando ele também for
uma das referências do pedido.

Limitação expressamente aceita: alterações cadastrais externas durante o fluxo
podem produzir uma atribuição incompatível com a situação atual do responsável.
A política de revalidação e correção desses pedidos fica para evolução
posterior, antes de disponibilizar manutenção cadastral.

O envio bloqueia a solicitação com `PESSIMISTIC_WRITE`. O recebimento faz uma
leitura preliminar para localizar o bem e verificar a permissão, sem concluir
nada sobre o estado; adquire os locks na ordem **bem -> solicitação**, compatível
com a criação. Ambos os registros são atualizados no contexto JPA após os locks,
e o ator é recarregado novamente após eventual espera. Aprovação, recusa,
cancelamento e envio bloqueiam somente a solicitação e não adquirem depois um
lock no bem. Nenhum caso novo introduz a ordem inversa solicitação -> bem.

Cada ação captura uma única vez o instante do servidor depois dos bloqueios e
validações, em UTC e com precisão de microssegundos. Estado, ator, data e evento
compartilham a mesma transação; no recebimento, a atualização do bem também faz
parte dela. Falhas de persistência são propagadas imediatamente para rollback,
sem continuar consultando ou gravando. Após `flush`, as aprovações são atualizadas
para montar a resposta completa mesmo se houve decisão concorrente durante a espera.

`RegrasExecucaoSolicitacao` compartilha autorização e estado entre as operações
e as ações `ENVIAR`/`RECEBER` do mapper, reutilizando a visibilidade existente.
Repetições e estados incompatíveis produzem `ESTADO_INCOMPATIVEL` sem novo evento
ou sobrescrita de atores e datas. Não há códigos públicos adicionais, cache de
respostas ou tradução HTTP implementada nesta entrega.

Os testes `EnvioRecebimentoSolicitacaoServiceIT` e
`ConcorrenciaEnvioRecebimentoSolicitacaoIT` foram escritos para sucesso,
permissões atuais, revalidação após espera, repetições, estados inválidos,
relógio sob bloqueio, política cadastral aprovada, preservação de atributos,
referências e decisões, e rollback do conjunto. As disputas cobrem envio contra
cancelamento, envio seguido de recebimento, repetição das ações, aprovação
seguida de envio, recebimento contra criação e mudanças externas durante a espera.

Após recebimento confirmado, o índice parcial libera novo pedido, cuja origem
é copiada do cadastro atualizado do bem. O futuro histórico de transferências
continua derivado exclusivamente das solicitações `CONCLUIDAS`, com suas
referências preservadas e registro de recebimento. Uma transferência posterior
não reescreve o pedido anterior. Não há outra tabela ou coleção de demonstração
como fonte de verdade nem implementação de consultas nesta entrega.

Validação manual final informada pelo responsável para a entrega de envio e
recebimento: **123 testes, zero falhas, zero erros, zero ignorados e
`BUILD SUCCESS`**. O resultado é registrado conforme informado, sem repetir
testes ou builds nesta revisão. Os resultados anteriores, incluindo os 99
testes da entrega de cancelamento, permanecem nas seções correspondentes.
A limitação cadastral e a política de evolução descritas acima permanecem vigentes.

Consultas, Controllers, endpoints, integração com autenticação e tradução HTTP de
`GESTOR_DESIGNADO_INVALIDO` permanecem pendentes. Migrations, contratos,
dependências e arquivos da autenticação não foram alterados.

## Evolução: consultas de cadastros e bens no service

`ConsultaPatrimonialService` reúne seleção de setores, localizações e usuários,
pesquisa paginada de bens e detalhe de bem. As operações são transacionais de
leitura e recebem o ator pela integração interna confiável, recarregado pelo ID
e validado como ativo. Não dependem de JWT, Controller ou endpoint. Qualquer
perfil ativo pode consultar bens e cadastros de outros setores, conforme o contrato.

Os DTOs e projeções pertencem à funcionalidade `consulta`, em `dto`, `service`,
`repository` e `enums`. Os módulos e entidades anteriores não foram reorganizados.
Setor/localização retornam ID, código e nome; usuário retorna ID, nome e setor
resumido, sem e-mail, perfil, hash ou outros dados de autenticação. Seletores não
têm paginação e ordenam por nome e ID. Setores/localizações devem estar ativos.

Definições aprovadas: a seleção de usuários exige `setorId` e retorna usuários
ativos desse setor mesmo quando ele está inativo. Setor inexistente produz
`RECURSO_NAO_ENCONTRADO`; setor sem usuários ativos retorna lista vazia. Os
filtros de bens aceitam setor e localização inativos existentes; referência
inexistente é erro mesmo quando não haveria resultados. Essas permissões de
consulta não flexibilizam as validações de criação de pedidos.

A busca remove espaços externos e trata texto vazio como ausência; pesquisa
parcialmente código, plaqueta ou descrição, sem distinguir caixa. `%` e `_`
digitados são literais, assim como o caractere de escape `!`. Valores são
vinculados como parâmetros. Setor, localização, busca e disponibilidade são
combinados; não há filtro implícito que exclua bens inativos. O detalhe expõe
as referências atuais mesmo quando inativas, sem reparar ou reativar cadastros.

Disponibilidade é calculada na própria consulta: bem ativo e nenhum pedido em
`AGUARDANDO_GESTOR_AREA`, `AGUARDANDO_GESTOR_RESPONSAVEL`, `APROVADA` ou
`EM_TRANSITO`. O vínculo com pedidos ativos não recebe filtro de visibilidade.
O índice único parcial existente garante no máximo um vínculo ativo por bem.
Os joins dos cadastros retornam os campos projetados sem navegação LAZY por item;
contagem e conteúdo compartilham o mesmo construtor de predicados.

A lista retorna o formato paginado do contrato, ordenado por código e ID. O
detalhe mantém os campos na raiz e acrescenta `solicitacaoEmAndamento`, somente
com ID/número quando o ator puder consultar o pedido. Caso contrário é `null`,
sem exposição desses dados por outro campo. A disponibilidade continua falsa
para pedido oculto. A regra de visibilidade existente agora também aceita IDs
históricos projetados; o método por entidade delega à mesma avaliação, preservando
setores participantes, conta ativa e consulta global administrativa.

Paginação: página padrão zero, tamanho padrão 20, limites 1 a 100. O deslocamento
é calculado em `long`. Como [Query.setFirstResult recebe int](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/Query.html#setFirstResult(int)),
a consulta usa `LIMIT`/`OFFSET` SQL parametrizados e vincula o deslocamento como
`Long`, sem conversão para `int`. Após contar, páginas além do fim retornam
conteúdo vazio mantendo os totais, inclusive deslocamentos superiores ao limite
de `int`. O cálculo de total de páginas usa divisão/resto para evitar overflow.

Os testes `ConsultaCadastrosServiceIT` e `ConsultaBensServiceIT` foram escritos
para elegibilidade, campos retornados, filtros combinados, busca literal, limites,
ordenação, paginação e ausência de resultados, estados ativos/finais, bens e
referências inativos, autenticação interna e preservação da regra de visibilidade.
Há cenários de pedido oculto indisponível, mudança de setor/perfil persistidos,
OFFSET SQL superior a `int` e rejeição de criação apesar da consulta permitida.
Os testes de quantidade de consultas comparam poucos e muitos itens com contexto
JPA limpo, caches de segundo nível e consultas desativados e deltas das estatísticas
de SQL do Hibernate; não dependem de uma contagem fixa incidental ou de cache quente.

Consultas não reservam bens nem substituem as validações sob bloqueio da criação.
Disponibilidade, dados e totais são informativos no momento de suas consultas.
No registro inicial desta entrega, compilação e execução dos novos testes ficaram
pendentes de autorização; não foram executados Docker, Maven, testes ou builds
naquela etapa. O resultado manual
informado de 123 testes da entrega de envio e recebimento permanece no histórico,
sem extensão desse resultado às consultas. Histórico de transferências e consultas
de solicitações ficam para entregas posteriores, usando os pedidos como fonte de
verdade. Controllers, endpoints, integração com autenticação e tradução HTTP de
`GESTOR_DESIGNADO_INVALIDO` continuam pendentes. Migrations, contratos, dependências
e arquivos da autenticação permanecem intactos.

### Revisão final das consultas — 04/11/2026

Validação manual final informada pelo responsável: **144 testes, zero falhas,
zero erros, zero ignorados e `BUILD SUCCESS`**. Esse resultado encerra a pendência
de compilação e execução registrada acima. Os resultados anteriores, incluindo
os 123 testes de envio e recebimento, permanecem como histórico. Não foram
repetidos testes ou builds durante a revisão final.

A revisão do diff e dos 14 arquivos novos não identificou credenciais reais nem
arquivos gerados na entrega. `git diff --check` e a verificação equivalente dos
arquivos novos, por `git diff --no-index --check`, não apontaram erros de whitespace.

O repository concreto de consultas, os predicados compartilhados e a projeção
interna são proporcionais aos filtros, à disponibilidade e ao OFFSET em `long`.
Os dois testes de quantidade de consultas verificam crescimento por item com
contexto JPA limpo e caches desativados, sem impor um número fixo de comandos SQL.
Não foram identificadas abstrações ou infraestrutura dispensáveis que exijam
simplificação. O mapeamento posicional das 17 colunas exige cuidado ao alterar
a projeção; isso é um ponto de manutenção, sem mudança proposta nesta entrega.

Na revisão histórica dessa entrega, estavam pendentes histórico de transferências,
consultas de solicitações, Controllers, endpoints, integração com autenticação e
tradução HTTP de `GESTOR_DESIGNADO_INVALIDO`. As evoluções posteriores e o quadro
atual da documentação funcional distinguem o que já foi implementado. A limitação
cadastral aprovada permanece válida.

## Evolução: tratamento de erros HTTP — 05/11/2026

As referências anteriores à tradução pendente de `GESTOR_DESIGNADO_INVALIDO`
registram o estado das entregas originais. A decisão aprovada nesta entrega
encerra essa lacuna: criação e decisão traduzem o motivo para
`500 / ERRO_INTERNO`, com mensagem genérica e `erros: []`. Não se trata de
campo escolhido pelo cliente nem de falta de permissão do ator; a designação
é responsabilidade do servidor. As transações e o rollback dos services
permanecem preservados.

`SOLICITANTE_INVALIDO` e `SOLICITANTE_INATIVO` são traduzidos para
`401 / NAO_AUTENTICADO`, sem distinguir publicamente essas condições.
`PERFIL_NAO_PERMITIDO` usa `403 / ACAO_NAO_PERMITIDA` e
`REFERENCIA_NAO_ENCONTRADA` usa `404 / RECURSO_NAO_ENCONTRADO`.
Os demais motivos seguem o status e código homônimos do contrato.

Na entrega original de tratamento de erros, a camada HTTP adicionou `erro/dto/ErroResponse`,
`erro/dto/ErroCampoResponse` e `erro/controller/TratamentoErrosControllerAdvice`,
sob `br.edu.simpi`. Não cria hierarquia de exceções nem catálogo adicional.
O advice herda o tratamento MVC do Spring e padroniza falhas de entrada em 400,
incluindo JSON inválido, corpo obrigatório ausente, conversão, binding e
validação. Falhas globais sem campo e JSON ilegível usam lista vazia. Mensagens
de topo e mensagens MVC são controladas em português; falhas por campo dos
services preservam as mensagens funcionais existentes. Valores rejeitados,
causas e mensagens técnicas não são publicados. `solicitacaoExistenteId` é omitido.

404 de rota, 405, 406 e 415 mantêm status, headers e tratamento padrão do Spring.
A definição de seus corpos permanece pendente para a revisão dos endpoints.
O acordo original previa integrar filtros/handlers de segurança com os mesmos
DTOs e revisar o fallback do advice. Essa integração já está implementada no
código documentado: exceções de segurança seguem à cadeia responsável, sem
conversão em 500 ou resposta duplicada. A pendência histórica de integração está
encerrada; as traduções de negócio aprovadas nesta seção permanecem preservadas.

Os testes MVC usam MockMvc standalone e controllers registrados explicitamente
apenas em `src/test`, com Bean Validation e sem contexto de banco. Cobrem a
matriz completa de motivos, formato, validação, conversões, JSON, erro global,
ocultação de detalhes e status/headers MVC preservados. Na implementação inicial,
compilação e execução ficaram pendentes de validação manual, encerrada abaixo.

### Revisão final do tratamento de erros — 05/11/2026

Execução manual de `verify -Pintegration` informada pelo responsável, concluída
às 19:14:52 (-03:00): **167 testes de integração, zero falhas, zero erros,
zero ignorados e `BUILD SUCCESS`**.

Consultados somente os relatórios Surefire da última execução para a contagem
MVC/unitária, atualizados em 05/11/2026 às 19:13:54:

- `TratamentoErrosControllerAdviceTest`: **43 testes MVC**, zero falhas,
  zero erros e zero ignorados.
- `TesteNumeroSolicitacao`: **3 testes unitários**, zero falhas, zero erros
  e zero ignorados.

Total Surefire: **46 testes**, separado dos 167 testes de integração informados.
O relatório residual de `BackendApplicationTests`, de 01/11/2026, não integra
essa contagem. Nenhum número MVC/unitário foi inferido do resumo do Failsafe.
Esses resultados encerram a pendência de compilação e execução desta entrega.
Não foram repetidos testes ou builds nesta revisão.

A revisão confirmou que `tratarConversoesDeRotaEQuery` envia cada valor inválido
uma única vez e acrescenta `setorId=1` apenas quando outro parâmetro está sob
teste. O caso consta como aprovado no Surefire; expectativas de 400, advice e
conversores permanecem preservados.

Revisados os três arquivos de produção, o arquivo de testes e os três documentos
da entrega. Não foram identificadas credenciais reais ou arquivos gerados entre
as alterações; as strings sensíveis nos testes são fictícias e verificam sua
ocultação. Autenticação, migrations, dependências e endpoints de negócio
permanecem fora das alterações.

Continua pendente a definição dos corpos de 404 de rota, 405, 406 e 415 na revisão
dos endpoints. A integração com segurança e a revisão do fallback já existem no
código documentado, conforme a atualização da seção acima.
