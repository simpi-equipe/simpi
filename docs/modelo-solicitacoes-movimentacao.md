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
