# SIMPI — Sistema Integrado de Movimentação Patrimonial e Inventário

Protótipo acadêmico voltado à organização das movimentações patrimoniais
e do inventário. Este repositório reúne o código e a documentação
produzidos pela equipe.

## Estado atual

O código deste checkout inclui:

- Autenticação JWT: login e consulta de identidade em `/api/auth/me`.
- Oito rotas GET de consulta de cadastros, bens, transferências e solicitações.
- Swagger UI/OpenAPI com springdoc 3.2.0 e coleção Insomnia para testes manuais.
- Criação de solicitações em `POST /api/solicitacoes`, com usuário autenticado, `201` e `Location`.
- Aprovação, recusa e cancelamento autenticados, reutilizando os services existentes.
- Services de envio e recebimento; seus Controllers HTTP permanecem pendentes.
- Seed optativo de desenvolvimento com cadastros fictícios e senha informada na execução.
- Migrations V1 e V2, Hibernate em validação e bancos separados para desenvolvimento e integração.
- Testes unitários, MVC/OpenAPI e de integração, além dos workflows de CI.

Este resumo descreve o código documentado; não comprova integração à `main`,
execução bem-sucedida em outro ambiente ou disponibilidade em deploy.
Veja o [estado e as evidências das entregas](docs/documentacao-funcional.md#estado-do-código-documentado).

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
│   ├── contratos-api-v1.1.md
│   ├── der-simplificado.png
│   ├── docker-backend.md
│   ├── documentacao-funcional.md
│   ├── entidades-e-atributos.md
│   ├── enums.md
│   ├── modelo-solicitacoes-movimentacao.md
│   ├── regras-e-fluxos.md
│   └── testes-manuais/insomnia/simpi-consultas.json
├── .env.example
├── .gitignore
├── AGENTS.md
├── compose.yaml
└── README.md
```

Consulte o [backend](backend/), a [documentação](docs/),
o [Compose](compose.yaml) e os [workflows](.github/workflows/).

Consulte a [documentação funcional](docs/documentacao-funcional.md) para o modelo, as regras e os contratos da API.

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

O [guia operacional do backend](docs/docker-backend.md) é a fonte principal
para variáveis, comandos e diagnóstico. Comece pela
[configuração JWT](docs/docker-backend.md#configurar-jwt); copiar o
[modelo de ambiente](.env.example) não fornece uma chave pronta.

## Iniciar PostgreSQL e backend

Escolha um modo de execução:

- [Banco em Docker e backend no IntelliJ](docs/docker-backend.md#banco-em-docker-e-backend-no-intellij).
- [Banco e backend em Docker](docs/docker-backend.md#banco-e-backend-em-docker).

Spring Boot e IntelliJ não carregam `.env` automaticamente. O Compose lê o
arquivo para interpolação; o backend em contêiner o recebe por `--env-file`.
Configure as variáveis explicitamente no processo Java ou na Run Configuration.

## Bancos e portas

Veja a [tabela de endereços e portas](docs/docker-backend.md#bancos-e-portas).
Dentro da rede Docker o banco usa `postgres:5432`; no Windows, use
`127.0.0.1` e a porta publicada. Desenvolvimento e integração têm bancos
e volumes separados; não use o banco de desenvolvimento para os testes de integração.

## API e testes manuais

- [Swagger e OpenAPI](docs/docker-backend.md#swagger-e-openapi): login, Authorize e consultas autenticadas.
- [Coleção Insomnia](docs/docker-backend.md#coleção-insomnia): importação, ambiente e sequência de chamadas.
- [Dados locais](docs/docker-backend.md#preparar-dados-locais): setor/gestor, usuário COMUM e BCrypt.
- [Seed de desenvolvimento](docs/seed-desenvolvimento.md): carga optativa e roteiro de validação da criação.
- [Contrato v1.1](docs/contratos-api-v1.1.md): referência funcional de parâmetros, respostas e erros.

## Testes e build

Os comandos de [testes e build](docs/docker-backend.md#testes) distinguem
unitários/MVC/OpenAPI, integração com PostgreSQL e validação manual.
O Dockerfile executa `verify` sem o perfil `integration`: inclui os testes
`*Test`/`*Tests`, mas não os `*IT` nem um banco externo.

Resultados de execuções anteriores são evidências históricas, não garantia
de sucesso do checkout atual. As limitações dos testes manuais estão no
[resumo das validações](docs/documentacao-funcional.md#evidências-de-validação).

## O que o CI valida

O [workflow Backend CI](.github/workflows/backend-ci.yml):

1. Obtém o código e configura Java 21.
2. Inicia um PostgreSQL 18 temporário, separado dos bancos locais.
3. Compila o backend, executa testes unitários/MVC/OpenAPI e os de integração existentes
   e gera o JAR usando o Maven Wrapper:

```bash
bash ./mvnw --batch-mode --no-transfer-progress verify -Pintegration
```

Esse comando roda dentro da pasta [backend](backend/), conforme
o diretório de trabalho definido no workflow.

O workflow atual valida as regras cobertas pelos testes existentes. Não constrói
a imagem Docker, não faz deploy nem valida o frontend. Um resultado de CI não
substitui a validação manual da API ou a comprovação de um deploy.

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

- Resolver e validar as pendências técnicas registradas na
  [documentação funcional](docs/documentacao-funcional.md#decisões-técnicas-e-pendências-funcionais).
- Evoluir migrations e entidades somente quando a próxima entrega aprovada exigir.
- Implementar os Controllers de envio e recebimento, reutilizando os services existentes.
- Implementar as telas e escolher as bibliotecas adicionais do frontend.
- Conferir a versão e os requisitos do futuro PostgreSQL no Supabase
  antes de planejar a conexão remota.

A [documentação funcional](docs/documentacao-funcional.md) está disponível,
com entidades, enums, fluxos, contratos da API e pendências técnicas.
O guia operacional é [Execução local e testes do backend](docs/docker-backend.md).
