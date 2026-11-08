# Regras e fluxos do MVP

Resumo operacional das decisões já registradas no [contrato da API v1.1](contratos-api-v1.1.md), que contém os formatos completos de requisição, resposta e erro. Este resumo não amplia o escopo.

## Escopo

- Um bem por solicitação e apenas uma solicitação ativa por bem.
- Cadastros preparados para demonstração; sem CRUD administrativo completo, anexos ou integração com SCI/ERP.
- Responsável por bem é um usuário do protótipo. Cada usuário pertence a um setor; cada setor possui um gestor designado.
- O gestor designado pertence ao setor gerenciado e possui perfil GESTOR,
  conforme regra original restaurada por confirmação do responsável.
- Localizações são independentes dos setores.
- Bem inativo não pode iniciar uma transferência. O responsável de destino precisa estar ativo e pertencer ao setor de destino.
- Pelo menos setor, localização ou responsável de destino deve diferir da origem.

## Consultas de cadastros e bens

- Seletores de setores e localizações retornam somente ativos, sem paginação,
  por nome e ID. Localizações são independentes do setor selecionado.
- A seleção de usuários exige `setorId` existente e retorna os usuários ativos
  desse setor, inclusive quando o setor está inativo. Consultar outro setor
  é permitido; não há exclusão de perfis na seleção. Setor inexistente é erro;
  setor sem usuários ativos retorna lista vazia.
- Filtros de bens aceitam setor e localização inativos, desde que existam.
  Isso não torna essas referências elegíveis para abrir pedidos: as validações
  de criação permanecem obrigatórias e inalteradas.
- A busca remove espaços externos; texto vazio equivale a ausência. Busca
  parcialmente em código, plaqueta ou descrição, sem distinguir caixa;
  `%` e `_` são caracteres literais. Todos os filtros são combinados.
- Bens inativos continuam consultáveis, com `ativo = false` e indisponibilidade.
  Disponibilidade exige bem ativo e ausência de pedido em andamento, mesmo que
  o ator não tenha acesso a esse pedido. É informativa, sem reserva do bem.
- No detalhe, `solicitacaoEmAndamento` contém somente ID e número se o ator
  puder consultar o pedido pela regra existente de visibilidade; caso contrário
  é `null`. A lista de bens não expõe dados do pedido. Um pedido oculto continua
  bloqueando a disponibilidade e o filtro de disponíveis não pode revelá-lo.
- Paginação começa em zero, com tamanho padrão 20 e limites 1 a 100; ordenação
  por código e ID. Página além dos resultados retorna conteúdo vazio com os
  totais da pesquisa, inclusive quando o deslocamento ultrapassa `int`.

## Fila de aprovação

A visão `AGUARDANDO_MINHA_APROVACAO` inclui somente etapas efetivamente
decidíveis pelo ator: conta ativa, perfil GESTOR, aprovador registrado igual
ao usuário, decisão pendente, estado correspondente e setor atual igual ao
setor da etapa (solicitante em GESTOR_AREA; origem em GESTOR_RESPONSAVEL).
Mudar para outro setor participante mantém a consulta quando houver acesso,
mas exclui o pedido dessa fila e impede APROVAR/RECUSAR. O aprovador registrado
é preservado, sem reatribuição de etapas.

## Relacionamentos principais

![DER conceitual simplificado](der-simplificado.png)

O desenho resume as ligações centrais. As referências adicionais de gestor, aprovador, autor, origem e destino constam no dicionário de atributos. Setor possui usuários e bens; localização contém bens; usuário é responsável por bens e abre solicitações; bem possui solicitações no histórico; solicitação possui aprovações e eventos.

## Caminho principal

| Ator / ação | Condição | Resultado |
| --- | --- | --- |
| Usuário COMUM cria | Bem e destino válidos | AGUARDANDO_GESTOR_AREA; primeira aprovação pendente e evento CRIACAO |
| Usuário ADMINISTRADOR cria | Bem e destino válidos | Mesmo fluxo de COMUM: GESTOR_AREA e depois GESTOR_RESPONSAVEL |
| Usuário GESTOR cria | Bem e destino válidos | AGUARDANDO_GESTOR_RESPONSAVEL; somente a etapa necessária é criada |
| Gestor designado aprova GESTOR_AREA | Aprovação pendente e estado correspondente | AGUARDANDO_GESTOR_RESPONSAVEL; criar segunda aprovação pendente |
| Gestor designado aprova GESTOR_RESPONSAVEL | Aprovação pendente e estado correspondente | APROVADA |
| Usuário ativo do setor de origem envia | APROVADA | EM_TRANSITO; registrar ator, data e evento ENVIO |
| Usuário ativo do setor de destino recebe | EM_TRANSITO | CONCLUIDA; atualizar cadastro do bem, ator, data e evento RECEBIMENTO |

O gestor da primeira etapa é o designado para o setor do solicitante. O da segunda etapa é o gestor designado para o setor de origem do bem. Autoaprovação é permitida quando o solicitante é o aprovador designado. Se o mesmo gestor ocupa as duas etapas, precisa tomar duas decisões explícitas. Pedidos criados por gestor não recebem uma primeira etapa ficticiamente aprovada.

## Recusa e cancelamento

- Gestor designado pode recusar sua etapa pendente, com justificativa obrigatória. Solicitação passa para RECUSADA.
- Somente o solicitante pode cancelar, com motivo obrigatório, enquanto aguarda uma aprovação ou está APROVADA e ainda não foi enviada.
- O cancelamento exige solicitante ativo e acesso ao pedido pelas regras de
  visibilidade aprovadas: setor atual participante como solicitante, origem
  ou destino históricos, ou consulta global do ADMINISTRADOR. Mudar de setor
  mantém o cancelamento possível se o acesso permanecer. Sem esse acesso,
  retornar ACAO_NAO_PERMITIDA antes de revelar estado ou decisões. A exceção
  de visibilidade administrativa não permite cancelar pedidos de outros
  solicitantes; nenhum perfil substitui a autoria do pedido.
- Não há cancelamento após o envio, reabertura ou reversão de estados finais no MVP.
- RECUSADA, CANCELADA e CONCLUIDA são estados finais; pedidos nesses estados não bloqueiam nova solicitação do bem.
- Recusa e cancelamento preservam o cadastro do bem e o histórico de decisões.

## Dados históricos e consistência

- Origem do bem e setor do solicitante são preservados na abertura; destino proposto fica registrado no pedido.
- O backend determina solicitante, atores, datas, estado e aprovações; não aceita esses dados como autoridade do cliente.
- Criar pedido, aprovação inicial e evento é uma única transação.
- Cada ação deve revalidar usuário, permissão, estado e disponibilidade durante sua execução.
- O cadastro atual do bem só muda no recebimento. Atualizar o bem, concluir o pedido e registrar o evento ocorre na mesma transação.
- Política aprovada para envio e recebimento no protótipo: usar os setores
  históricos para autorizar o ator ativo e aplicar exatamente setor, localização
  e responsável de destino registrados no pedido. Alterações ou inativações
  posteriores do bem ou dessas referências não bloqueiam as operações.
  O recebedor não substitui o responsável de destino; não reativar cadastros
  nem modificar outros atributos do bem. A conta do ator continua exigindo
  situação ativa, inclusive quando ele também é uma referência do pedido.
- Limitação aceita: alterações cadastrais externas durante o fluxo podem
  produzir uma atribuição incompatível com a situação atual do responsável.
  A política de revalidação e correção desses pedidos fica para evolução
  posterior, antes de disponibilizar manutenção cadastral.
- Eventos são acrescentados, sem edição ou exclusão pela aplicação.
- Histórico de transferências deriva dos pedidos concluídos e de suas referências preservadas; não do cadastro atual do bem.
- O histórico de um bem, inclusive inativo, aplica a visibilidade dos pedidos
  antes da contagem e paginação. Ordena por recebimento e ID da solicitação,
  ambos decrescentes. Bem inexistente retorna RECURSO_NAO_ENCONTRADO; sem
  transferências visíveis retorna página vazia. Página além do fim mantém os
  totais visíveis. Nomes podem refletir os cadastros atuais, sem snapshots.
- Nenhum clique repetido ou requisição concorrente pode duplicar eventos, criar dois pedidos ativos ou decidir a próxima etapa involuntariamente.
- Aprovar e recusar identificam solicitacaoId e aprovacaoId. A mesma aprovação repetida não decide outra etapa.
- Não há Idempotency-Key obrigatório nem cache de respostas nesta versão. Em perda de resposta, consultar o estado atual antes de tentar novamente.

## Permissões de consulta e sessão

- Todas as operações do catálogo exigem autenticação, exceto POST /api/auth/login.
- Usuários autenticados consultam bens; pedidos ficam visíveis aos setores participantes e ao administrador conforme o contrato.
- ADMINISTRADOR tem consulta global, sem receber automaticamente o direito de aprovar, enviar ou receber.
- ADMINISTRADOR pode abrir solicitações pelo mesmo fluxo de COMUM, com aprovação do gestor do seu setor e depois do gestor de origem do bem; o perfil não dispensa aprovações.
- JWT com validade inicial configurável de 60 minutos. Verificar assinatura, expiração e usuário ativo, perfil e setor atuais em cada requisição.
- Token somente em memória do frontend; atualizar a página exige login novamente, limitação aceita no MVP.
- Logout local não revoga o token. Não há refresh token nesta versão.
- Senhas são armazenadas com hash BCrypt; não registrar senhas ou tokens nos logs.

## Regras para testes prioritários

Validar fluxo comum, caminho de gestor, autoaprovação, duas decisões do mesmo gestor, recusa e cancelamento. Verificar bloqueio por ator incorreto, ação em estado inválido, repetição e concorrência. Comprovar que o bem permanece inalterado antes do recebimento e que falhas de gravação provocam rollback de toda a operação.
