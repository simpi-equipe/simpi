# Aprovação, recusa e cancelamento pela API

As três ações usam a identidade do JWT e retornam `200` com a solicitação
completa atualizada. O [contrato v1.1](contratos-api-v1.1.md) é a referência de
rotas, permissões e erros. Os services e as migrations foram preservados.

Se o backend já estava iniciado antes de compilar esta entrega, reinicie-o
com a versão atual. Em Docker, reconstrua a imagem e recrie somente o
contêiner do backend conforme o [guia operacional](docker-backend.md).
Executar os testes não atualiza automaticamente um processo ou contêiner antigo.

## Aprovar as duas etapas

1. Crie um pedido com COMUM ou ADMINISTRADOR. Consulte o detalhe e copie o ID
   da aprovação pendente em `GESTOR_AREA`.
2. Faça login com o gestor designado para essa etapa e atualize o Authorize
   do Swagger ou o token do Insomnia.
3. Envie `POST /api/solicitacoes/{solicitacaoId}/aprovacoes/{aprovacaoId}/aprovar`,
   sem corpo. O resultado deve ser `AGUARDANDO_GESTOR_RESPONSAVEL`, com a
   primeira aprovação decidida e uma segunda aprovação pendente com outro ID.
4. Repetir a chamada para o primeiro ID deve retornar
   `409 ETAPA_JA_DECIDIDA`, sem aprovar a segunda etapa.
5. Faça login com o gestor de origem designado na segunda aprovação. Use
   esse novo ID na rota para aprovar explicitamente a segunda etapa. O pedido
   deve ficar `APROVADA`; os dados do patrimônio permanecem iguais.

Mesmo quando o gestor é a mesma pessoa nas duas etapas, as duas chamadas
precisam identificar aprovações diferentes. Pedidos abertos por GESTOR
começam diretamente em `GESTOR_RESPONSAVEL`.

Para os cadastros DEMO, o gestor de DEMO-AREA é `gestor.area@simpi.example`,
e o de DEMO-ORIGEM é `gestor.origem@simpi.example`. Consulte o pedido para
confirmar a designação antes de agir. A senha é a informada na execução do
seed; nenhuma senha ou token deve ser incluído como evidência de teste.

## Recusar outro pedido

Use um pedido diferente, ainda aguardando a etapa do gestor autenticado.
Envie `POST /api/solicitacoes/{solicitacaoId}/aprovacoes/{aprovacaoId}/recusar`:

```json
{
  "justificativa": "O equipamento é necessário para a origem."
}
```

Espere `RECUSADA`, decisão de aprovação `RECUSADA` e evento `RECUSA` com a
justificativa. O bem fica disponível para outro pedido, sem alteração de
setor, localização ou responsável. Texto vazio ou só espaços retorna `400`.

## Cancelar um pedido próprio

Faça login com o solicitante do pedido. Envie
`POST /api/solicitacoes/{id}/cancelar`:

```json
{
  "motivo": "A necessidade foi atendida por outro equipamento."
}
```

Espere `CANCELADA` e evento `CANCELAMENTO`. As decisões já tomadas são
preservadas, etapas pendentes não são decididas e o bem fica disponível.
Cancelamento é permitido somente em `AGUARDANDO_GESTOR_AREA`,
`AGUARDANDO_GESTOR_RESPONSAVEL` ou `APROVADA`. Estados finais ou trânsito
retornam `409 ESTADO_INCOMPATIVEL`; outro ator retorna `403`.

## Cuidados na validação

- Os testes de integração usam exclusivamente `simpi_test` e fixtures sintéticas.
- Testes pelo Swagger/Insomnia criam e alteram registros reais do banco de
  desenvolvimento. Não execute a coleção inteira em lote.
- Use pedidos diferentes para demonstrar aprovação, recusa e cancelamento.
- Se uma ação falhar por rede, consulte o detalhe antes de repetir; não presuma
  que o servidor deixou de registrar a decisão.
- Envio e recebimento ainda não estão expostos por HTTP nesta entrega.
