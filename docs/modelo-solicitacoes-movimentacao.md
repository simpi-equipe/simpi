# Modelo de solicitações e movimentação

Decisões técnicas aprovadas para a evolução da fundação patrimonial. Referências:
[dicionário](entidades-e-atributos.md), [enums](enums.md),
[regras](regras-e-fluxos.md) e [API v1.1](contratos-api-v1.1.md).

## Escopo e migração

A V2 acrescenta `solicitacao`, `aprovacao` e `evento_solicitacao`, sem modificar
a V1 ou o contrato HTTP. Flyway continua responsável pelo esquema e Hibernate
somente valida. Não há endpoints, autenticação, DTOs HTTP ou execução de ações
do fluxo nesta entrega. Os repositories possuem somente `JpaRepository`.

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
da futura API permanece ISO 8601 em UTC com `Z`; a sessão do banco pode exibir
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

## Responsabilidades dos futuros casos de uso

- Revalidar usuário ativo, permissões, perfil, setor atual e aprovador designado.
  Abertura por ADMINISTRADOR permanece indefinida e não é disponibilizada.
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

Não há triggers de transição, deduplicação de eventos, autenticação ou
implementação dessas operações nesta etapa. Constraints por linha não garantem
sozinhas a atomicidade entre as tabelas.

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
A validação corresponde à suíte executada nesta entrega. Implementação e testes
dos futuros casos de uso, permissões e transições permanecem pendentes na frente
do fluxo.

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
