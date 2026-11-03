# Enums do SIMPI

Valores transcritos do catálogo de 23/10/2026 e conferidos com o contrato da API v1.1 de 27/10/2026. As explicações de cancelamento e administração foram alinhadas às decisões posteriores, sem acrescentar valores.

Identificadores aprovados nos contratos são preservados. Não substituir status por situacao, por exemplo, sem revisão explícita do contrato.

## 1 PerfilUsuario

**Utilizado em:** usuario.perfil

| Valor | Significado |
| --- | --- |
| COMUM | Usuário que consulta bens, abre solicitações e acompanha os pedidos permitidos para sua área. |
| GESTOR | Usuário que analisa solicitações e exerce as funções de gestão previstas para seu setor. |
| ADMINISTRADOR | Usuário com consulta global; esse perfil não concede automaticamente permissão para aprovar, enviar ou receber. |

O perfil é combinado com o setor e a etapa da solicitação para verificar a autorização de cada ação. Não concede acesso irrestrito por si só.

## 2 StatusSolicitacao

**Utilizado em:** solicitacao.status; evento_solicitacao.status_anterior; evento_solicitacao.status_novo

| Valor | Significado |
| --- | --- |
| AGUARDANDO_GESTOR_AREA | Pedido aguardando análise do gestor da área do solicitante. |
| AGUARDANDO_GESTOR_RESPONSAVEL | Pedido aguardando análise do gestor responsável pela aprovação referente ao bem. |
| APROVADA | Todas as aprovações necessárias foram obtidas. O envio pode ser registrado. |
| EM_TRANSITO | A origem registrou o envio. O destino ainda não confirmou o recebimento. |
| CONCLUIDA | O destino confirmou o recebimento e os dados atuais do bem foram atualizados. |
| RECUSADA | Uma das etapas de aprovação recusou o pedido, com justificativa. |
| CANCELADA | Solicitação encerrada pelo solicitante antes do envio, conforme as permissões e estados definidos no contrato. |

RECUSADA, CONCLUIDA e CANCELADA são estados finais nesta proposta. No evento de criação, status_anterior fica vazio. Não é necessário criar enums diferentes para os estados dos eventos.

## 3 EtapaAprovacao

**Utilizado em:** aprovacao.etapa

| Valor | Significado |
| --- | --- |
| GESTOR_AREA | Análise do gestor do setor do solicitante. Aplicável aos pedidos abertos por usuário comum. |
| GESTOR_RESPONSAVEL | Análise do gestor responsável pela aprovação referente ao bem solicitado. |

Pedidos abertos por gestores seguem diretamente à etapa GESTOR_RESPONSAVEL. Não se cria uma etapa GESTOR_AREA ficticiamente aprovada.

## 4 DecisaoAprovacao

**Utilizado em:** aprovacao.decisao

| Valor | Significado |
| --- | --- |
| PENDENTE | A etapa existe, mas o gestor ainda não registrou sua decisão. |
| APROVADA | O gestor aprovou essa etapa. Isso não significa aprovação final se ainda houver outra etapa pendente. |
| RECUSADA | O gestor recusou essa etapa. Exige justificativa e encerra o pedido como RECUSADA. |

Valor inicial: PENDENTE. O atributo decidida_em permanece vazio até a aprovação ou recusa.

## 5 TipoEventoSolicitacao

**Utilizado em:** evento_solicitacao.tipo

| Valor | Significado |
| --- | --- |
| CRIACAO | Registro da abertura do pedido e de seu estado inicial. |
| APROVACAO | Registro da aprovação de uma etapa. A etapa e a decisão ficam detalhadas em aprovacao. |
| RECUSA | Registro da recusa de uma etapa de aprovação. |
| ENVIO | Registro da saída do bem pela origem. |
| RECEBIMENTO | Registro da confirmação de recebimento pelo destino. |
| CANCELAMENTO | Registro do cancelamento do pedido. |

O evento descreve o que aconteceu; o status descreve a situação do pedido após a ação. São conceitos distintos, mesmo quando os nomes são semelhantes.
