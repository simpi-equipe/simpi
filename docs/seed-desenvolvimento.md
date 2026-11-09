# Seed de desenvolvimento

O perfil `seed-desenvolvimento` prepara cadastros fictícios para login, consultas
e criação de solicitações. Ele não é ativado por padrão, não faz parte das
migrations e não usa as fixtures dos testes de integração.

## Senha das contas de demonstração

As sete contas fictícias usam **`SimpiDemo@2026`** quando a carga é executada
com o comando abaixo. Essa é uma senha pública de demonstração, exclusiva
do ambiente local de desenvolvimento.

## Executar

Inicie o PostgreSQL de desenvolvimento e configure `DB_URL`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` e `JWT_SECRET` no processo Java, conforme o
[guia operacional](docker-backend.md). O Maven e o Spring não carregam `.env`
automaticamente.

O seed exige conexão pelo host local, por exemplo
`jdbc:postgresql://127.0.0.1:5434/simpi_dev`. Confira a porta publicada no seu
ambiente; o padrão do Compose é 5432. O banco `simpi_test` é bloqueado. URLs
remotas, parâmetros na URL e o hostname Docker `postgres` também são bloqueados:
execute o backend no host para esta carga, ou use a alternativa Docker abaixo,
que compartilha a rede do PostgreSQL e acessa o banco por loopback.

Na raiz do repositório, em PowerShell 7, com as variáveis acima configuradas:

```powershell
$env:SIMPI_SEED_SENHA = 'SimpiDemo@2026'
$env:SPRING_PROFILES_ACTIVE = "seed-desenvolvimento"
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

Após as migrations, a aplicação executa a carga em uma transação e continua
rodando para os testes HTTP. A senha informada é compartilhada somente pelas
sete contas fictícias e cada conta recebe um hash BCrypt próprio. A senha de
demonstração está documentada acima; senhas de outros ambientes e hashes não
devem ser publicados. O seed continua exigindo a variável explícita, sem senha
padrão na configuração da aplicação.

Depois de encerrar a aplicação, remova as opções temporárias antes de voltar
ao modo normal:

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE, Env:SIMPI_SEED_SENHA
```

### Alternativa com Docker

Com o PostgreSQL de desenvolvimento ativo e a imagem `simpi-backend:local`
construída, execute na raiz do repositório:

```powershell
docker run -d --name simpi-seed --network container:simpi-local-postgres-1 --env-file .env -e DB_URL=jdbc:postgresql://127.0.0.1:5432/simpi_dev -e SPRING_PROFILES_ACTIVE=seed-desenvolvimento -e SIMPI_SEED_SENHA=SimpiDemo@2026 simpi-backend:local
docker logs -f simpi-seed
```

Aguarde `Seed de desenvolvimento concluído` ou a indicação de cadastros já
presentes. Essa execução usa a rede do contêiner PostgreSQL local, sem publicar
outra porta HTTP. Após a carga, saia dos logs com Ctrl+C e encerre somente
o contêiner temporário:

```powershell
docker stop simpi-seed
docker rm simpi-seed
```

O backend normal pode ser iniciado com `docker start simpi-backend`. Essa carga
não limpa o banco: recriação dos dados exige uma limpeza explícita e autorizada.

## Dados disponíveis

| Setor | Contas |
| --- | --- |
| DEMO-AREA | gestor.area@simpi.example, comum.area@simpi.example, admin.area@simpi.example |
| DEMO-ORIGEM | gestor.origem@simpi.example, comum.origem@simpi.example |
| DEMO-DESTINO | gestor.destino@simpi.example, comum.destino@simpi.example |

Cada setor tem seu gestor designado, uma localização com o mesmo código do
setor e dois bens: por exemplo, `DEMO-ORIGEM-1` e `DEMO-ORIGEM-2`. O responsável
pelos bens é o usuário comum daquele setor. Todos começam ativos. IDs vêm das
sequências do banco; consulte-os pela API, sem presumir números fixos.

São 3 setores, 7 usuários, 3 localizações e 6 bens. Não são criadas solicitações,
aprovações ou eventos. O administrador pertence ao setor DEMO-AREA e segue o
mesmo fluxo de abertura do usuário comum.

Os códigos e e-mails listados são reservados para esta carga. Uma reexecução
com todos os registros presentes não altera dados, senhas ou movimentações.
Se houver apenas parte dos registros, a carga falha antes de inserir; investigue
os conflitos sem apagar registros automaticamente. A carga não restaura o
estado inicial de bens que já foram movimentados.

## Validar a criação

1. Faça login com `comum.area@simpi.example` ou `admin.area@simpi.example`.
2. Consulte o bem `DEMO-ORIGEM-1`, os seletores de setor/localização e os
   usuários de DEMO-DESTINO. Copie os IDs reais.
3. Envie `POST /api/solicitacoes` com o bem e destino escolhidos e uma
   justificativa. Espere `201`, `Location` e `AGUARDANDO_GESTOR_AREA`, com
   aprovação pendente para o gestor de DEMO-AREA.
4. Consulte a rota de `Location` e confira a solicitação e seu evento de criação.
5. Repita o POST para o mesmo bem: espere `409 BEM_COM_SOLICITACAO_EM_ANDAMENTO`.
6. Para testar GESTOR, use outro bem disponível e faça novo login. O primeiro
   estado esperado é `AGUARDANDO_GESTOR_RESPONSAVEL`, sem decisão automática.

A coleção [Insomnia](testes-manuais/insomnia/simpi-consultas.json) inclui
requisições de criação, conflito, aprovação, recusa e cancelamento. O
[roteiro de ações](acoes-solicitacoes.md) orienta as trocas de ator e pedidos.
Os endpoints de envio e recebimento também estão disponíveis; siga o roteiro
para validar o fluxo completo com os usuários de origem e destino.
