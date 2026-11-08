# SIMPI Contratos da API

Versão 1.1 - MVP acadêmico - 27 de outubro de 2026

Referência para implementação e integração entre React e Spring Boot. Consolida as decisões de escopo, requisições, respostas e permissões acordadas pela equipe. Os exemplos usam dados fictícios; este contrato não comprova que a API já esteja implementada. Os exemplos da solicitação 43 representam momentos distintos do fluxo, como a espera pela primeira aprovação e a transferência concluída no histórico; não compõem um único conjunto de dados inicial.

Revisão 1.1: aprovação e recusa identificam a aprovação específica na rota. Removidos o cabeçalho obrigatório Idempotency-Key e o armazenamento de respostas repetidas. Mantidos os demais fluxos e a sessão definida para o MVP.

## Operações previstas

| Método | Rota | Finalidade |
| --- | --- | --- |
| POST | /api/auth/login | Autenticar |
| GET | /api/auth/me | Consultar identidade atual |
| GET | /api/setores | Selecionar setor |
| GET | /api/localizacoes | Selecionar localização |
| GET | /api/usuarios | Selecionar responsável por setor |
| GET | /api/bens | Pesquisar e filtrar bens |
| GET | /api/bens/{id} | Consultar bem |
| GET | /api/bens/{id}/transferencias | Consultar transferências concluídas |
| POST | /api/solicitacoes | Criar pedido |
| GET | /api/solicitacoes | Listar pedidos permitidos |
| GET | /api/solicitacoes/{id} | Consultar pedido completo |
| POST | /api/solicitacoes/{solicitacaoId}/aprovacoes/{aprovacaoId}/aprovar | Aprovar etapa pendente |
| POST | /api/solicitacoes/{solicitacaoId}/aprovacoes/{aprovacaoId}/recusar | Recusar com justificativa |
| POST | /api/solicitacoes/{id}/cancelar | Cancelar com motivo |
| POST | /api/solicitacoes/{id}/enviar | Registrar envio |
| POST | /api/solicitacoes/{id}/receber | Confirmar recebimento |

## Limites desta versão

Um bem por solicitação; cadastros previamente preparados; sem integração com SCI/ERP, anexos, renovação automática de token, recuperação de senha, cadastro público ou telas administrativas completas. O seletor de perfis do Figma é exclusivo da demonstração.

Leitura do documento: convenções; autenticação; cadastros; bens e histórico; listagens e criação de pedidos; resposta completa; ações; erros e repetição de chamadas; integração e evoluções registradas.


---

# Convenções e permissões

| Item | Definição |
| --- | --- |
| Formato | JSON; campos camelCase; Content-Type: application/json nos corpos. |
| Identificação | IDs numéricos nas rotas. numero é a identificação visual gerada pelo backend, como SOL-2026-0043. |
| Data e hora | ISO 8601 em UTC, com Z. Frontend exibe em pt-BR e America/Sao_Paulo. Datas históricas são preservadas. |
| Ausência de valor | Campos opcionais previstos na resposta usam null; arrays sem itens usam []. |
| Paginação | pagina começa em 0; padrão tamanho=20; mínimo 1 e máximo 100. |
| Autenticação | Somente POST /api/auth/login é público entre as operações deste catálogo. Demais rotas exigem Bearer token. |
| Resposta das ações | As cinco ações retornam 200 e o objeto completo da solicitação atualizado. POST de criação retorna 201 e o mesmo formato. |

```json
{
  "conteudo": [],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 0,
  "totalPaginas": 0
}
```

Lista vazia retorna 200. Totais consideram todos os filtros e a permissão de acesso; não representam o conjunto global se houver restrições.

## Acesso e responsabilidade pelo cálculo

Usuários autenticados consultam os bens. Pedidos são visíveis se o setor atual do usuário coincide com o setor solicitante, origem ou destino preservados no pedido. Administradores têm consulta global, sem receber automaticamente o direito de aprovar, enviar ou receber.

O responsável de destino deve estar ativo e pertencer ao setor de destino. Localizações não são obrigatoriamente vinculadas a setores. Autor, origem, datas, etapas, decisões e estado são determinados pelo backend.

## Enums utilizados

| Domínio | Valores |
| --- | --- |
| PerfilUsuario | COMUM, GESTOR, ADMINISTRADOR |
| StatusSolicitacao | AGUARDANDO_GESTOR_AREA, AGUARDANDO_GESTOR_RESPONSAVEL, APROVADA, EM_TRANSITO, CONCLUIDA, RECUSADA, CANCELADA |
| EtapaAprovacao | GESTOR_AREA, GESTOR_RESPONSAVEL |
| DecisaoAprovacao | PENDENTE, APROVADA, RECUSADA |
| TipoEventoSolicitacao | CRIACAO, APROVACAO, RECUSA, ENVIO, RECEBIMENTO, CANCELAMENTO |


---

# Autenticação e sessão

```http
POST /api/auth/login
```

E-mail e senha obrigatórios. Validar formato do e-mail e remover espaços externos do e-mail; preservar exatamente a senha. Login exige conta ativa. Resposta de sucesso: 200.

```json
{
  "email": "usuario@example.com",
  "senha": "senha-de-demonstracao"
}
```

```json
{
  "accessToken": "<token-jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "usuario": {
    "id": 10,
    "nome": "Usuário de demonstração 10",
    "email": "usuario@example.com",
    "perfil": "COMUM",
    "setor": { "id": 1, "nome": "Administrativo" }
  }
}
```

```http
GET /api/auth/me
```

Retorna 200 com o objeto usuario mostrado acima, diretamente na raiz, sem accessToken, tokenType ou expiresIn. Não renova o token.

```http
Authorization: Bearer <token-jwt>
```

## Decisões de sessão do MVP

| Decisão | Comportamento |
| --- | --- |
| Validade inicial | 60 minutos a partir da emissão. Configuração jwt.expiration em milissegundos; expiresIn na resposta em segundos acompanha essa configuração. Não é prazo de inatividade. |
| Armazenamento | Somente em memória do frontend, sem localStorage ou sessionStorage. |
| Recarregamento | Atualizar a página perde o token e exige novo login. Limitação de experiência registrada para evolução. |
| Expiração | Frontend limpa a sessão e encaminha ao login ao receber 401. |
| Saída | Botão Sair limpa token e dados locais. Não há rota de logout nem revogação individual antecipada. |
| Validação | Backend valida assinatura e expiração e verifica usuário ativo, perfil e setor atuais em cada requisição. |
| Senha e transporte | Senha com BCrypt por PasswordEncoder; HTTPS na publicação; sem senha ou token nos logs. |

Sair não invalida um token já emitido: ele permanece utilizável até expirar, salvo bloqueio pelo usuário inativo. A API não aceita o seletor de perfis do Figma como autenticação. Evolução para preservar sessão está descrita na última seção.


---

# Cadastros para seleção

Listas pequenas, sem paginação; somente registros ativos; ordenação por nome e id como desempate. Não retornam senha, hash nem dados de autenticação. São consultas autenticadas para preencher formulários.

```http
GET /api/setores
```

```json
[
  { "id": 1, "codigo": "ADM", "nome": "Administrativo" },
  { "id": 2, "codigo": "TI", "nome": "Tecnologia" }
]
```

```http
GET /api/localizacoes
```

```json
[
  { "id": 6, "codigo": "LOC-006", "nome": "Secretaria - 1º andar" },
  { "id": 9, "codigo": "LOC-009", "nome": "Auditório técnico" }
]
```

O mesmo catálogo alimenta o formulário de destino e o filtro de bens. Selecionar setor não restringe a localização. Não há criação de localização pelo formulário de transferência.

```http
GET /api/usuarios?setorId=1
```

```json
[
  {
    "id": 10,
    "nome": "Usuário de demonstração 10",
    "setor": { "id": 1, "nome": "Administrativo" }
  },
  {
    "id": 11,
    "nome": "Gestor de demonstração 11",
    "setor": { "id": 1, "nome": "Administrativo" }
  }
]
```

setorId é obrigatório. Consultar outro setor é permitido para escolher o responsável de destino. Ao trocar o setor no formulário, limpar o responsável selecionado e carregar novamente esta lista.

## Resultados e erros

200 com array, inclusive vazio. 400 para setorId ausente ou com formato inválido. 404 para setor inexistente. Um setor existente sem usuários ativos retorna [].


---

# Consulta de bens

```http
GET /api/bens
```

| Parâmetro opcional | Finalidade |
| --- | --- |
| busca | Pesquisa parcial em código, plaqueta ou descrição; sem distinguir maiúsculas e minúsculas. |
| setorId | Setor atual do bem. |
| localizacaoId | Localização atual, por identificador. |
| disponivelParaSolicitacao | true ou false. |
| pagina / tamanho | Padrões: 0 / 20. Todos os filtros são combinados. |

Ordenação por codigo crescente e id crescente como desempate. Disponibilidade = bem ativo e sem solicitação em andamento; não representa situação física ou contábil.

```json
{
  "conteudo": [
    {
      "id": 3,
      "codigo": "PAT-2022-0311",
      "plaqueta": "001104",
      "descricao": "Projetor Epson PowerLite X49",
      "setor": { "id": 2, "nome": "Tecnologia" },
      "localizacao": { "id": 9, "nome": "Auditório técnico" },
      "responsavel": { "id": 13, "nome": "Usuário de demonstração 13" },
      "ativo": true,
      "disponivelParaSolicitacao": true
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

```http
GET /api/bens/{id}
```

Retorna 200 com os campos de um item da lista diretamente na raiz, acrescentando solicitacaoEmAndamento. O histórico possui endpoint próprio.

```json
{
  "id": 43,
  "numero": "SOL-2026-0043"
}
```

O objeto acima é o valor de solicitacaoEmAndamento, e não a resposta completa. O campo é null se não houver pedido ou se o usuário não puder consultá-lo. disponivelParaSolicitacao permanece false quando houver bloqueio, mesmo sem revelar o pedido. Bem inexistente retorna 404.


---

# Histórico de transferências

```http
GET /api/bens/{id}/transferencias
```

Lista paginada de solicitações CONCLUIDAS do bem, ordenada por recebidaEm decrescente e solicitacaoId decrescente como desempate. Aplica as mesmas permissões de consulta dos pedidos; administrador tem acesso global.

```json
{
  "conteudo": [
    {
      "solicitacaoId": 43,
      "numero": "SOL-2026-0043",
      "recebidaEm": "2026-10-24T19:15:00Z",
      "origem": {
        "setor": { "id": 2, "nome": "Tecnologia" },
        "localizacao": { "id": 9, "nome": "Auditório técnico" },
        "responsavel": { "id": 13, "nome": "Usuário de demonstração 13" }
      },
      "destino": {
        "setor": { "id": 1, "nome": "Administrativo" },
        "localizacao": { "id": 6, "nome": "Secretaria - 1º andar" },
        "responsavel": { "id": 10, "nome": "Usuário de demonstração 10" }
      }
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

## Regras de origem dos dados

Consultar as solicitações concluídas, incluindo as recém-concluídas. Não usar uma coleção fixa de demonstração nem reconstruir transferências a partir do cadastro atual do bem.

Origem e destino são os dados preservados no pedido. Uma transferência posterior não pode sobrescrever as referências históricas do pedido anterior. Renomear registros de cadastro não está no escopo deste MVP; congelar também os nomes exibidos pode ser avaliado em uma evolução de auditoria.

O frontend deve indicar que o histórico corresponde às transferências disponíveis para consulta quando houver restrição de acesso. 200 com conteudo=[] se não houver transferências visíveis; 404 se o bem não existir.

Bens inativos também possuem histórico consultável, com as mesmas regras de visibilidade. Página além do fim retorna conteudo=[] mantendo totalElementos e totalPaginas dos pedidos visíveis. Bem inexistente retorna RECURSO_NAO_ENCONTRADO.


---

# Listagem de solicitações

```http
GET /api/solicitacoes
```

Filtros opcionais: busca (número do pedido ou código do bem, parcial e sem distinguir maiúsculas); status (StatusSolicitacao); visao; pagina; tamanho. Combinar filtros com as permissões. Ordenar por criadaEm e id, ambos decrescentes.

| visao | Regra |
| --- | --- |
| DA_MINHA_AREA | Padrão. Setor do usuário participa como solicitante, origem ou destino. |
| CRIADAS_POR_MIM | Usuário autenticado é o solicitante. |
| AGUARDANDO_MINHA_APROVACAO | Exclusiva de usuário ativo com perfil GESTOR: etapa pendente designada ao usuário, pedido aguardando essa etapa e pertencimento atual ao setor da etapa (solicitante para GESTOR_AREA; origem para GESTOR_RESPONSAVEL), conforme as regras de aprovação. Não altera o aprovador registrado nem reatribui etapas; a exclusão dessa fila não impede consulta por outras visões quando houver acesso. |
| ENVIOS_E_RECEBIMENTOS | Fila operacional: APROVADA com origem no setor do usuário, ou EM_TRANSITO com destino nesse setor. |
| TODAS | Exclusiva de administrador: pedidos de todas as áreas. |

```json
{
  "conteudo": [
    {
      "id": 43,
      "numero": "SOL-2026-0043",
      "criadaEm": "2026-10-24T18:30:00Z",
      "status": "AGUARDANDO_GESTOR_AREA",
      "bem": {
        "id": 3, "codigo": "PAT-2022-0311",
        "descricao": "Projetor Epson PowerLite X49"
      },
      "solicitante": {
        "id": 10, "nome": "Usuário de demonstração 10",
        "setor": { "id": 1, "nome": "Administrativo" }
      },
      "setorOrigem": { "id": 2, "nome": "Tecnologia" },
      "setorDestino": { "id": 1, "nome": "Administrativo" }
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

Uma visão autorizada sem itens retorna 200 com lista vazia. Pedir uma visão incompatível com o perfil retorna 403. O setor do solicitante na resposta é o preservado na abertura do pedido.


---

# Criação de solicitação

```http
POST /api/solicitacoes
```

Uma solicitação trata de um único bem. Todos os campos abaixo são obrigatórios.

```json
{
  "bemId": 3,
  "setorDestinoId": 1,
  "localizacaoDestinoId": 6,
  "responsavelDestinoId": 10,
  "justificativa": "Projetor necessário para reuniões do setor."
}
```

| Campo | Validação |
| --- | --- |
| bemId | Bem existente, ativo e sem pedido em andamento. |
| setorDestinoId | Setor existente e ativo. |
| localizacaoDestinoId | Localização existente e ativa; independe do setor. |
| responsavelDestinoId | Usuário existente, ativo e pertencente ao setor de destino. |
| justificativa | Texto não vazio após remoção dos espaços externos. |

## Dados definidos pelo servidor

Identificar solicitante autenticado; preservar seu setor e os dados atuais de origem do bem; gerar id e numero; registrar data/hora; escolher a primeira etapa; criar aprovação pendente e evento CRIACAO.

Pelo menos um dado de destino (setor, localização ou responsável) deve diferir da origem. Revalidar o bloqueio do bem no momento da gravação, com controle de concorrência no backend. Não confiar apenas no botão desabilitado.

| Solicitante | Primeiro estado |
| --- | --- |
| COMUM | AGUARDANDO_GESTOR_AREA |
| ADMINISTRADOR | AGUARDANDO_GESTOR_AREA |
| GESTOR | AGUARDANDO_GESTOR_RESPONSAVEL |

ADMINISTRADOR pode abrir solicitações seguindo o mesmo fluxo de COMUM: aprovação pelo gestor do setor do solicitante e, depois, pelo gestor do setor de origem do bem. O perfil administrativo não dispensa etapas nem concede permissão de aprovação.

## Resposta

201 Created com o objeto completo apresentado na seção seguinte. Cabeçalho Location: /api/solicitacoes/43. A criação não altera o cadastro do bem. Não há Idempotency-Key obrigatório nesta rota; uma repetição após criação pode retornar 409 por bem já solicitado.

Falhas de preenchimento ou registros inativos: 400. Referência inexistente: 404. Usuário sem permissão: 403. Bem com solicitação em andamento: 409. Registrar pedido, aprovação e evento em uma única transação.


---

# Resposta completa da solicitação

```http
GET /api/solicitacoes/{id}
```

200 no sucesso. Este mesmo formato é retornado na criação (201) e nas cinco ações (200). Exemplo visto pela solicitante, antes da primeira aprovação.

```json
{
  "id": 43,
  "numero": "SOL-2026-0043",
  "status": "AGUARDANDO_GESTOR_AREA",
  "criadaEm": "2026-10-24T18:30:00Z",
  "justificativa": "Projetor necessário para reuniões do setor.",
  "bem": {
    "id": 3, "codigo": "PAT-2022-0311", "plaqueta": "001104",
    "descricao": "Projetor Epson PowerLite X49"
  },
  "solicitante": {
    "id": 10, "nome": "Usuário de demonstração 10",
    "setor": { "id": 1, "nome": "Administrativo" }
  },
  "origem": {
    "setor": { "id": 2, "nome": "Tecnologia" },
    "localizacao": { "id": 9, "nome": "Auditório técnico" },
    "responsavel": { "id": 13, "nome": "Usuário de demonstração 13" }
  },
  "destino": {
    "setor": { "id": 1, "nome": "Administrativo" },
    "localizacao": { "id": 6, "nome": "Secretaria - 1º andar" },
    "responsavel": { "id": 10, "nome": "Usuário de demonstração 10" }
  },
  "aprovacoes": [
    {
      "id": 70, "etapa": "GESTOR_AREA",
      "aprovador": { "id": 11, "nome": "Gestor de demonstração 11" },
      "decisao": "PENDENTE",
      "justificativa": null, "decididaEm": null
    }
  ],
  "envio": null,
  "recebimento": null,
  "eventos": [
    {
      "id": 100, "tipo": "CRIACAO",
      "autor": { "id": 10, "nome": "Usuário de demonstração 10" },
      "statusAnterior": null,
      "statusNovo": "AGUARDANDO_GESTOR_AREA",
      "descricao": "Solicitação encaminhada ao gestor da área.",
      "ocorridoEm": "2026-10-24T18:30:00Z"
    }
  ],
  "acoesDisponiveis": ["CANCELAR"]
}
```

aprovacoes é ordenado pelas etapas do fluxo; eventos é ordenado por ocorridoEm crescente e id crescente como desempate. Uma etapa futura ainda não criada não aparece como pendente. Ela pode ser desenhada como Não iniciada pelo frontend. Pedido inexistente: 404; sem acesso: 403.


---

# Aprovação e recusa

As ações exigem Bearer token, sem cabeçalho adicional de idempotência. Retornam 200 com o pedido completo atualizado; o servidor determina ator e datas. A etapa é identificada pela aprovação presente na rota. Não alterar o bem nestas operações.

```http
POST /api/solicitacoes/{solicitacaoId}/aprovacoes/{aprovacaoId}/aprovar
```

Sem corpo. solicitacaoId identifica o pedido e aprovacaoId identifica exatamente a etapa que será decidida. Somente gestor ativo designado para essa etapa pendente. Autoaprovação permitida quando o solicitante é o aprovador designado.

| Estado atual | Resultado |
| --- | --- |
| AGUARDANDO_GESTOR_AREA | Decisão APROVADA; criar segunda aprovação PENDENTE; estado AGUARDANDO_GESTOR_RESPONSAVEL. |
| AGUARDANDO_GESTOR_RESPONSAVEL | Decisão APROVADA; estado APROVADA. |

Registrar decididaEm e evento APROVACAO. Se o mesmo gestor ocupa as duas etapas, são necessárias duas decisões explícitas. Pedido criado por gestor possui somente a etapa GESTOR_RESPONSAVEL.

```http
POST /api/solicitacoes/{solicitacaoId}/aprovacoes/{aprovacaoId}/recusar
```

```json
{
  "justificativa": "O equipamento é necessário para a origem."
}
```

Mesma autorização de aprovador. Verificar que aprovacaoId pertence a solicitacaoId e corresponde à etapa que o pedido aguarda. Justificativa obrigatória, não vazia e não composta apenas de espaços.

Registrar a etapa como RECUSADA, justificativa e data; mudar pedido para RECUSADA; registrar evento RECUSA e liberar o bem. Não criar as etapas posteriores. O bem permanece com os dados atuais.

## Etapa e gestor designados

GESTOR_AREA recebe o gestor do setor solicitante preservado na abertura. GESTOR_RESPONSAVEL recebe o gestor do setor de origem preservado no pedido. O detentor do bem não precisa ser seu aprovador.

O aprovador é registrado na criação da etapa. Delegação, substituição automática e gestão de ausências estão fora do MVP. As duas etapas são distintas mesmo quando designadas à mesma pessoa.

## Bloqueios

403 se o usuário não for o aprovador autorizado. Aprovação inexistente ou que não pertence ao pedido informado retorna 404 APROVACAO_NAO_ENCONTRADA. Etapa já decidida retorna 409 ETAPA_JA_DECIDIDA; estado incompatível, como pedido cancelado, retorna 409 ESTADO_INCOMPATIVEL. Verificar o acesso antes de expor dados do pedido.

## Exemplo com o mesmo gestor nas duas etapas

```http
POST /api/solicitacoes/43/aprovacoes/70/aprovar
POST /api/solicitacoes/43/aprovacoes/71/aprovar
```

A primeira chamada decide somente a aprovação 70 e cria a 71 pendente. Repetir a chamada da 70 retorna 409 e nunca decide a 71. O frontend usa o id da aprovação pendente retornada no pedido atualizado para a segunda ação explícita. A recusa usa a mesma identificação de etapa.


---

# Cancelamento e execução

```http
POST /api/solicitacoes/{id}/cancelar
```

```json
{
  "motivo": "A necessidade foi atendida por outro equipamento."
}
```

Somente o solicitante ativo. Estados permitidos: AGUARDANDO_GESTOR_AREA, AGUARDANDO_GESTOR_RESPONSAVEL ou APROVADA. Motivo obrigatório e não vazio.

Mudar para CANCELADA; gerar evento CANCELAMENTO com motivo na descricao; preservar decisões anteriores. Aprovações pendentes não recebem novas decisões. Liberar o bem sem alterar seus dados. Não permitir cancelamento em trânsito nem reabertura.

```http
POST /api/solicitacoes/{id}/enviar
```

Sem corpo. Qualquer usuário ativo do setor de origem; estado obrigatório APROVADA. Mudar para EM_TRANSITO, registrar usuário e instante do envio e evento ENVIO. Manter o cadastro do bem inalterado.

```http
POST /api/solicitacoes/{id}/receber
```

Sem corpo. Qualquer usuário ativo do setor de destino; estado obrigatório EM_TRANSITO, com envio registrado.

Na mesma transação: registrar recebimento, atualizar setor/localização/responsável do bem a partir do destino preservado, concluir o pedido e gerar evento RECEBIMENTO. A pessoa que recebe não substitui automaticamente o responsável de destino.

## Blocos de envio e recebimento na resposta

Cada bloco fica null até a ação acontecer. Quando preenchido, possui a estrutura abaixo:

```json
{
  "realizadoEm": "2026-10-24T19:15:00Z",
  "realizadoPor": { "id": 11, "nome": "Gestor de demonstração 11" }
}
```

## Ações disponíveis por usuário

| Situação | Valores possíveis |
| --- | --- |
| Aprovador da etapa pendente | APROVAR e RECUSAR. |
| Solicitante antes do envio | CANCELAR, inclusive quando já aprovado. |
| Usuário da origem, pedido aprovado | ENVIAR. |
| Usuário do destino, pedido em trânsito | RECEBER. |
| Pedido encerrado ou sem ação permitida | Array vazio []. |

Condições são cumulativas. Gestor solicitante/aprovador pode receber [APROVAR, RECUSAR, CANCELAR]. Esta lista é um domínio da interface, não um novo estado de negócio. Verificar novamente a autorização ao executar cada ação.


---

# Erros e chamadas repetidas

```json
{
  "codigo": "DADOS_INVALIDOS",
  "mensagem": "Revise os campos informados.",
  "erros": [
    { "campo": "justificativa", "mensagem": "Informe a justificativa." }
  ]
}
```

| HTTP | Código | Aplicação |
| --- | --- | --- |
| 400 | DADOS_INVALIDOS | Campos, paginação, enums ou referências inativas inválidos. |
| 401 | CREDENCIAIS_INVALIDAS | Falha de login; mensagem genérica para conta inativa ou credenciais incorretas. |
| 401 | NAO_AUTENTICADO | Token ausente, inválido, expirado ou usuário desativado. |
| 403 | ACAO_NAO_PERMITIDA | Perfil, designação ou área não autorizados. |
| 404 | RECURSO_NAO_ENCONTRADO | Bem ou referência de cadastro inexistentes. |
| 404 | SOLICITACAO_NAO_ENCONTRADA | Pedido inexistente. |
| 404 | APROVACAO_NAO_ENCONTRADA | Aprovação inexistente ou não vinculada ao pedido da rota. |
| 409 | BEM_COM_SOLICITACAO_EM_ANDAMENTO | Conflito ao abrir novo pedido. |
| 409 | ESTADO_INCOMPATIVEL | Ação incompatível com o estado atual. |
| 409 | ETAPA_JA_DECIDIDA | Tentativa de decidir novamente uma etapa encerrada. |
| 500 | ERRO_INTERNO | Falha inesperada; mensagem genérica, sem stack trace. |

erros é [] quando não houver falhas por campo. No conflito por bem solicitado, pode incluir solicitacaoExistenteId, somente se o usuário puder consultar o pedido. Listas vazias retornam 200. Filtro com ID malformado: 400; com referência inexistente: 404.

## Convenções de tradução HTTP aprovadas — 05/11/2026

A resposta padronizada contém exatamente `codigo`, `mensagem` e `erros`.
Cada item de `erros` contém somente `campo` e `mensagem`. As mensagens públicas
são controladas em português; não expõem valores rejeitados, SQL, stack traces,
credenciais, causas ou mensagens técnicas de exceções. Os mapas de falhas por
campo dos services são convertidos para essa lista, em ordem estável por campo.

- JSON inválido, corpo obrigatório ausente, tipos incompatíveis, parâmetros
  obrigatórios ausentes, enums e validação de entrada: `400 / DADOS_INVALIDOS`.
- Quando não houver campo identificável com segurança, inclusive em erro global
  ou JSON malformado, retornar `erros: []`, sem inventar nomes de campo.
- `SOLICITANTE_INVALIDO` e `SOLICITANTE_INATIVO`: `401 / NAO_AUTENTICADO`,
  com mensagem genérica, sem distinguir ausência, inexistência ou inatividade.
- `PERFIL_NAO_PERMITIDO`: `403 / ACAO_NAO_PERMITIDA`.
- `REFERENCIA_NAO_ENCONTRADA`: `404 / RECURSO_NAO_ENCONTRADO`.
- `GESTOR_DESIGNADO_INVALIDO`, na criação ou na decisão de aprovação:
  `500 / ERRO_INTERNO`, mensagem genérica e `erros: []`. O gestor é designado
  pelo servidor; a falha cadastral não é atribuída aos campos nem à permissão
  do ator. Esta decisão encerra a lacuna anterior, sem criar código público.
- Demais motivos dos services usam o status e o código homônimos da tabela.
- Falhas inesperadas: `500 / ERRO_INTERNO`, mensagem genérica e `erros: []`.
- `solicitacaoExistenteId` permanece omitido nesta entrega. Sua inclusão continua
  opcional no contrato e depende de informação com visibilidade autorizada.

404 de rota, 405, 406 e 415 permanecem com o tratamento padrão do Spring,
preservando status e headers. A padronização de seus corpos fica **pendente para
a revisão dos endpoints**; não converter essas respostas em 400 ou 500 pelo fallback.

### Acordo para integração com segurança

O advice traduz erros MVC e as exceções de negócio dos services. A integração
com segurança prevista neste acordo foi implementada: filtros e handlers tratam
autenticação/autorização usando os mesmos DTOs `ErroResponse` e `ErroCampoResponse`.
Falha de login usa
`CREDENCIAIS_INVALIDAS`; falha de autenticação nas demais rotas usa `NAO_AUTENTICADO`.

O fallback do advice preserva exceções de autenticação e acesso negado, inclusive
quando encapsuladas, para a cadeia responsável; não as converte em 500 nem escreve
uma segunda resposta. O usuário autenticado é recebido como principal, sem
identidade arbitrária na requisição, e os services mantêm sua revalidação.
Essas informações descrevem a implementação do acordo, sem alterar os status,
códigos ou permissões aprovados. O estado das entregas está na
[documentação funcional](documentacao-funcional.md#estado-do-código-documentado).

## Proteção contra repetição e concorrência

Idempotency-Key não é obrigatório em nenhuma operação do MVP. Não há registro de respostas por chave nem promessa de reproduzir a resposta original em uma repetição. O frontend desabilita o botão enquanto a operação está em andamento.

Aprovar e recusar sempre identificam a aprovação específica. A validação de pendência e a gravação da decisão, do estado e do evento precisam ocorrer na mesma transação, com bloqueio de registro ou atualização condicional que permita somente um vencedor.

Envio, recebimento e cancelamento também verificam e alteram o estado de forma atômica. Duas ações concorrentes, como enviar e cancelar, não podem ser concluídas simultaneamente. A ação que encontrar o estado já alterado retorna 409, sem novo evento.

Na criação, verificar disponibilidade e criar o pedido de forma atômica, por exemplo serializando os pedidos no registro do bem. Isso impede duas solicitações ativas para o mesmo bem. Gerar o número único no backend, sem depender do tamanho de uma lista do frontend.

## Falha de rede após uma ação

Se a resposta se perder, a ação pode já ter sido concluída. Consultar GET /api/solicitacoes/{id} antes de oferecer nova tentativa. Uma repetição de decisão na mesma aprovação pode retornar 409 ETAPA_JA_DECIDIDA; outras transições repetidas podem retornar 409 ESTADO_INCOMPATIVEL. Recarregar detalhes e interpretar o estado e o histórico; não assumir que a primeira chamada falhou.

Criar solicitação após resposta perdida também pode retornar 409 BEM_COM_SOLICITACAO_EM_ANDAMENTO. Quando autorizado, solicitacaoExistenteId permite abrir o pedido já criado. Não disparar novas ações automaticamente ao atualizar a tela.


---

# Integração e evolução da sessão

## Comportamento esperado no frontend

Após login, guardar token e identidade em memória. Enviar Authorization em todas as consultas e ações. Em 401, limpar sessão e ir para o login; em 403, manter login e informar falta de permissão. Em 409, apresentar mensagem e atualizar o pedido antes de oferecer outra ação.

Ao receber uma ação bem-sucedida, substituir os detalhes pelo objeto retornado. Reconsultar o bem e seu histórico após recebimento; reconsultar disponibilidade após recusa ou cancelamento. O histórico de transferências deve incluir todos os pedidos concluídos visíveis, não apenas os dados iniciais.

As respostas são DTOs do contrato, não serialização direta das entidades JPA. Não expor senha_hash, campos internos desnecessários nem aceitar autor/status enviados pelo frontend como fonte de verdade.

## Critérios mínimos de integração

1. Login válido, inválido e usuário inativo; acesso sem token e com token expirado.

2. Criação por usuário comum e por gestor; responsável compatível com destino; bloqueio de pedido duplicado e de destino idêntico.

3. Duas aprovações por usuário comum; uma etapa por gestor; autoaprovação; mesmo gestor decidindo duas etapas explicitamente.

4. Recusa com justificativa; cancelamento pelo solicitante antes do envio; liberação do bem sem mudança cadastral.

5. Envio pela origem e recebimento pelo destino; rejeição de área incorreta e cancelamento após envio.

6. Cadastro do bem alterado somente no recebimento; origem e destino históricos preservados; nova transferência visível no histórico.

7. Pesquisa e filtros combinados; paginação estável; totais limitados por acesso; ações disponíveis coerentes.

8. Repetir a mesma aprovação retorna 409 sem decidir a próxima; chamadas simultâneas não duplicam eventos. Disputas entre envio e cancelamento e criações simultâneas do mesmo bem não deixam gravações parciais.

## Alinhamento com o protótipo validado

Manter as solicitações mais recentes primeiro na aplicação integrada, conforme o contrato, mesmo que o protótipo exiba outra ordem. Reconsultar o histórico após concluir uma transferência. As verificações no Figma não comprovam segurança, persistência ou concorrência do backend.

## Limitações aceitas e melhoria futura

Memória é uma escolha de armazenamento do token; sua perda no recarregamento é uma limitação de experiência, não prova de uma falha de segurança. A equipe aceitou novo login após atualizar a página para preservar o prazo do MVP.

Evolução recomendada: projetar persistência segura da sessão com cookie HttpOnly/Secure e/ou renovação de token, definindo expiração, revogação, proteção contra CSRF quando aplicável e comportamento entre frontend e backend. Não resolver a persistência simplesmente copiando o token para localStorage.

A validade inicial de 60 minutos é configurável, ainda sujeita à experiência de uso. Sem renovação e sem revogação individual, Sair encerra o acesso local, mas não invalida o token já emitido. Antes de uso real, revisar essa política e os controles de acesso e concorrência.

## Referências técnicas

Spring Security: docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
Spring Security: docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html
OWASP: cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html
