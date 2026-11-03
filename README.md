# SIMPI — Sistema Integrado de Movimentação Patrimonial e Inventário

Protótipo acadêmico voltado à organização das movimentações patrimoniais
e do inventário. Este repositório reúne o código e a documentação
produzidos pela equipe.

## Estado atual

A infraestrutura inicial do backend está configurada:

- Aplicação Spring Boot com suporte a validação, JPA e PostgreSQL.
- Construção e execução do backend com Docker.
- Bancos separados para desenvolvimento e integração.
- Flyway configurado para controlar futuras migrations.
- Hibernate configurado para validar o esquema, sem criar ou alterar tabelas.
- Teste de integração e workflow de CI do backend.

As funcionalidades de negócio, entidades e migrations de negócio ainda
não foram implementadas. Não há endpoints de negócio disponíveis.

O frontend mínimo usa React com JavaScript e Vite, com uma página de
identificação do SIMPI. Ele pode ser executado e compilado independentemente
do backend e dos bancos.

## Stack e pré-requisitos

| Componente | Tecnologia atual |
| --- | --- |
| Backend | Java 21 e Spring Boot 4.1.2 |
| Persistência | Spring Data JPA e driver PostgreSQL |
| Migrations | Flyway |
| Banco local e CI | PostgreSQL 18 |
| Build do backend | Maven Wrapper |
| Ambiente local | Docker e Docker Compose |
| CI | GitHub Actions |
| Frontend | React 19.3.0 e Vite 8.3.5, com JavaScript |
| Ambiente do frontend | Node.js 24.22.2 LTS e npm 11.19.1 |

As dependências do backend estão no [pom.xml](backend/pom.xml).

Para executar pelo Docker, instale Git e Docker com suporte a contêineres
Linux e ao Docker Compose. Java e Maven locais não são necessários
nesse modo.

Para executar pelo Java ou rodar os testes localmente, instale também
o JDK 21. Use o Maven Wrapper do repositório, sem instalar Maven
separadamente. O PostgreSQL pode continuar no Docker.

Os exemplos abaixo usam PowerShell e partem da raiz do repositório
após a clonagem. Os exemplos de senha com `Read-Host -MaskInput`
exigem PowerShell 7.

É necessário acesso à internet para baixar imagens e dependências.

PostgreSQL 18 é a versão escolhida para desenvolvimento local e CI.
A versão disponível no futuro projeto Supabase ainda deverá ser conferida.

## Estrutura atual

```text
simpi/
├── .github/
│   └── workflows/
│       ├── backend-ci.yml
│       └── frontend-ci.yml
├── backend/
│   ├── .mvn/
│   ├── src/
│   │   ├── main/
│   │   └── test/
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
├── frontend/
│   ├── src/
│   ├── .nvmrc
│   ├── index.html
│   ├── package.json
│   ├── package-lock.json
│   └── vite.config.js
├── docs/
│   └── docker-backend.md
├── .env.example
├── .gitignore
├── AGENTS.md
├── compose.yaml
└── README.md
```

Consulte o [backend](backend/), a [documentação](docs/),
o [Compose](compose.yaml) e os [workflows](.github/workflows/).

## Frontend: instalação, execução e build

Instale Node.js 24.22.2 LTS, que inclui npm 11.19.1. A versão do Node
também está registrada em [frontend/.nvmrc](frontend/.nvmrc).
Partindo da raiz do repositório:

```powershell
cd frontend
npm ci
npm run dev
```

Abra o endereço informado pelo Vite, normalmente http://localhost:5173.
Para compilar e, opcionalmente, conferir o resultado localmente:

```powershell
npm run build
npm run preview
```

O build gera `frontend/dist/`. O lockfile deve ser versionado;
`node_modules/`, `dist/` e arquivos `.env` reais são ignorados.
O frontend atual não exige `.env`, credenciais, backend ou banco.
Bibliotecas adicionais serão escolhidas pela pessoa responsável pelo frontend.

## Preparar o ambiente local

Clone o repositório e entre na pasta do projeto:

```powershell
git clone https://github.com/simpi-equipe/simpi.git
cd simpi
```

Crie o arquivo de ambiente a partir do [modelo versionado](.env.example),
somente se o destino ainda não existir:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Revise os valores do seu `.env` antes de iniciar os serviços.
Preserve configurações existentes e use credenciais exclusivas
para desenvolvimento.

O arquivo real é ignorado pelo [.gitignore](.gitignore).
Não force sua inclusão no Git nem publique credenciais em commits,
documentação ou PRs.

As credenciais de inicialização do PostgreSQL são aplicadas quando
o volume está vazio. Alterar o arquivo de ambiente não reconfigura
automaticamente um banco já inicializado.

## Iniciar PostgreSQL e backend

### Backend pelo Docker

Inicie o banco de desenvolvimento e aguarde o healthcheck:

```powershell
docker compose up -d --wait postgres
```

Construa a imagem e crie o contêiner do backend:

```powershell
docker build -t simpi-backend:local ./backend
docker run -d --name simpi-backend --network simpi-local_default --env-file .env -p 127.0.0.1:8080:8080 simpi-backend:local
```

O endereço local é http://127.0.0.1:8080. Uma resposta HTTP 404 na raiz
não indica, por si só, falha de inicialização: ainda não há endpoint
de negócio nesse endereço.

Se o contêiner já existir, consulte o guia antes de criá-lo novamente.
O [guia do backend com Docker](docs/docker-backend.md) explica logs,
confirmação da inicialização, parada, reinício e reconstrução.

### Backend diretamente pelo Java

Com o banco de desenvolvimento saudável, use outro terminal PowerShell 7.
Ajuste banco, usuário e porta conforme sua configuração local:

```powershell
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5432/simpi_dev"
$env:POSTGRES_USER = "simpi_dev"
$env:POSTGRES_PASSWORD = Read-Host -MaskInput "Senha do banco de desenvolvimento"
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

O Spring Boot não lê `.env` automaticamente. O Compose o usa para
interpolação; o backend em Docker recebe as variáveis pelo argumento
de arquivo de ambiente; a execução pelo Java recebe as variáveis
definidas no terminal.

Execute apenas uma instância do backend na mesma porta.

## Bancos e portas

| Uso | Serviço | Banco | Porta na máquina |
| --- | --- | --- | --- |
| Desenvolvimento | `postgres` | Configurado por `POSTGRES_DB`; exemplo `simpi_dev` | `POSTGRES_PORT`, padrão `5432` |
| Integração local | `postgres-test` | `simpi_test` | `5433`, definida no Compose |
| Backend | Contêiner `simpi-backend` ou Java local | — | `8080` por padrão |

Os bancos usam contêineres e volumes separados. Não aponte os testes
de integração para o banco de desenvolvimento.

As portas dos bancos são publicadas somente em `127.0.0.1`.
Dentro da rede Docker, PostgreSQL continua na porta `5432`;
o backend em contêiner usa `postgres:5432`.

Se a porta de desenvolvimento estiver ocupada, escolha outra em
`POSTGRES_PORT`, como `5434`, e execute novamente a inicialização
do serviço. Clientes executados na máquina devem usar a mesma porta.

A porta de integração é definida no [Compose](compose.yaml);
se ela mudar, ajuste também a URL dos testes. Para conflitos na porta
do backend ou no nome do contêiner, consulte
[Problemas comuns](docs/docker-backend.md#problemas-comuns).
Não remova bancos ou volumes para resolver conflitos de porta.

## Testes e build

Os comandos Maven locais exigem JDK 21.

### Sem integração

```powershell
.\backend\mvnw.cmd -f backend/pom.xml test
.\backend\mvnw.cmd -f backend/pom.xml verify
```

Os testes unitários futuros usarão nomes como `*Test` e `*Tests`.
Atualmente não existem testes unitários de negócio.

Sem o perfil de integração, a verificação compila e empacota o backend,
mas não executa [BackendApplicationIT](backend/src/test/java/br/edu/simpi/BackendApplicationIT.java).
O build do [Dockerfile](backend/Dockerfile) usa esse modo e não exige
banco externo.

### Integração com PostgreSQL

Inicie o serviço exclusivo de testes:

```powershell
docker compose --profile integration up -d --wait postgres-test
```

Em um terminal PowerShell 7 exclusivo para testes:

```powershell
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5433/simpi_test"
$env:POSTGRES_USER = "simpi_test"
$env:POSTGRES_PASSWORD = Read-Host -MaskInput "Senha definida em POSTGRES_TEST_PASSWORD"
.\backend\mvnw.cmd -f backend/pom.xml verify -Pintegration
```

O perfil Maven `integration` executa testes com nomes `*IT`.
A cobertura atual verifica a inicialização do contexto, uma consulta
`SELECT 1` e a validação do Flyway.

Isso ainda não valida funcionalidades, consultas ou migrations de negócio,
pois elas não foram implementadas.

Use terminais separados para desenvolvimento e testes, evitando reutilizar
as variáveis do banco errado. Veja também o
[guia de testes](docs/docker-backend.md#testes).

## O que o CI valida

O [workflow Backend CI](.github/workflows/backend-ci.yml):

1. Obtém o código e configura Java 21.
2. Inicia um PostgreSQL 18 temporário, separado dos bancos locais.
3. Compila o backend, executa os testes de integração existentes
   e gera o JAR usando o Maven Wrapper:

```bash
bash ./mvnw --batch-mode --no-transfer-progress verify -Pintegration
```

Esse comando roda dentro da pasta [backend](backend/), conforme
o diretório de trabalho definido no workflow.

O workflow atual não constrói a imagem Docker, não faz deploy
e não valida frontend ou funcionalidades de negócio.

Ele roda em PRs destinados à `main`, em pushes para `main`,
`chore/estrutura-inicial` e `chore/postgres-local`, e também permite
execução manual. Outras branches são validadas ao abrir PR para `main`.

O [workflow Frontend CI](.github/workflows/frontend-ci.yml) usa Node.js 24,
cache npm baseado em `frontend/package-lock.json`, `npm ci` e
`npm run build`, dentro de `frontend/`, com permissão `contents: read`.
Ele é independente do backend e do banco e não faz deploy nem exige
credenciais configuradas pela equipe.

O CI do frontend roda em PRs destinados à `main` e em pushes para `main`
e `chore/frontend-js`, quando há alterações em `frontend/` ou no próprio
workflow. Também permite execução manual.

## Como contribuir

Antes de começar, confira o estado do repositório e preserve qualquer
alteração local. Com a árvore de trabalho limpa:

```powershell
git status --short --branch
git switch main
git pull --ff-only origin main
git switch -c docs/nome-da-tarefa
```

Use um nome adequado à tarefa, como `docs/...`, `chore/...`,
`feat/...` ou `fix/...`. Se a atualização não puder ocorrer por
fast-forward, investigue a diferença antes de continuar; não descarte
alterações para forçar a atualização.

Durante o trabalho:

1. Limite as alterações ao objetivo da branch.
2. Atualize a documentação quando mudar comandos ou configuração.
3. Execute as validações relevantes e revise o diff antes do commit.
4. Faça commits com mensagens claras e envie sua branch.
5. Abra um PR para `main`, descrevendo objetivo, validações e pendências.
6. Confira os checks do CI, solicite revisão e resolva os comentários
   antes do merge.

Não inclua credenciais ou arquivos gerados nos commits.

Ao trabalhar com o agente, siga [AGENTS.md](AGENTS.md): comandos e edições
exigem autorização prévia; commit, push e exclusões exigem autorização
específica.

## Próximas etapas

- Documentar entidades, fluxos patrimoniais, contratos da API
  e regras de negócio.
- Criar migrations versionadas pelo Flyway e entidades correspondentes.
- Implementar regras de negócio e endpoints com testes adequados.
- Implementar as telas e escolher as bibliotecas adicionais do frontend.
- Conferir a versão e os requisitos do futuro PostgreSQL no Supabase
  antes de planejar a conexão remota.

A documentação funcional ainda está pendente. O guia operacional
existente é [Backend com Docker](docs/docker-backend.md).
