# Backend com Docker

## Pré-requisitos

- Docker instalado e em execução, com suporte a contêineres Linux.
- Acesso à internet para baixar imagens e dependências.
- Execute todos os comandos abaixo na raiz do repositório.

- Para executar o backend, inicie também o PostgreSQL local conforme
  os comandos abaixo. A construção da imagem não exige banco.
- O arquivo .env contém credenciais locais e não deve ser versionado.

Java e Maven locais não são necessários: a imagem usa Java 21
e o Maven Wrapper para compilar e verificar o projeto.

## Construir a imagem

```powershell
docker build -t simpi-backend:local ./backend
```

O contexto de construção é `backend`. O `.dockerignore` exclui,
entre outros arquivos, `target/`, logs e arquivos `.env`.

## Preparar o PostgreSQL local

PostgreSQL 18 é a versão escolhida para o ambiente local e o CI.
A versão disponível no futuro projeto Supabase ainda deverá ser
conferida antes da integração remota.

Na raiz do repositório, copie o exemplo somente se ainda não existir
um .env; preserve qualquer configuração local existente:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Inicie o banco e aguarde o healthcheck:

```powershell
docker compose up -d --wait postgres
```

Os dados persistem no volume postgres_data. Para parar os serviços
preservando os dados:

```powershell
docker compose down
```

Alterar usuário, senha ou nome do banco no .env não reconfigura
um volume já inicializado. O usuário criado pela imagem é
administrador deste banco local; não reutilize as credenciais
em ambientes remotos.

O Compose lê .env para interpolação. O backend recebe essas
variáveis pelo argumento --env-file; Spring Boot não lê esse
arquivo automaticamente.

O DB_URL do exemplo usa postgres, nome acessível na rede
simpi-local_default. Para executar Java diretamente na máquina,
exporte as variáveis no processo e use 127.0.0.1 e a porta publicada.

## Executar

Se houver um contêiner da validação anterior criado com `--rm`,
pare-o antes; ele será removido automaticamente. Depois execute
o comando abaixo para criar o contêiner persistente.

```powershell
docker run -d --name simpi-backend --network simpi-local_default --env-file .env -p 127.0.0.1:8080:8080 simpi-backend:local
```

O backend fica acessível em `http://127.0.0.1:8080`, somente
pela máquina local.

A inicialização já foi validada manualmente, com Tomcat na porta
8080 e a mensagem `Started BackendApplication`.

A estrutura inicial ainda não implementa a API de negócio.
Uma resposta HTTP 404 na raiz não indica, por si só, falha
na inicialização.

## Consultar logs

```powershell
docker logs -f simpi-backend
```

Use Ctrl+C para sair do acompanhamento; o contêiner continua
em execução.

## Parar e iniciar novamente

```powershell
docker stop simpi-backend
docker start simpi-backend
```

## Reconstruir após alterações

Reconstrua a imagem e recrie o contêiner para aplicar as mudanças:

```powershell
docker build -t simpi-backend:local ./backend
docker stop simpi-backend
docker rm simpi-backend
docker run -d --name simpi-backend --network simpi-local_default --env-file .env -p 127.0.0.1:8080:8080 simpi-backend:local
```

A remoção acima exclui o contêiner anterior. Apenas reiniciá-lo
não aplica a nova imagem.

## Problemas comuns

### Porta 8080 ocupada

Pare o serviço que ocupa a porta ou publique em outra porta local:

```powershell
docker run -d --name simpi-backend --network simpi-local_default --env-file .env -p 127.0.0.1:8081:8080 simpi-backend:local
```

Nesse caso, acesse `http://127.0.0.1:8081`.

### Nome de contêiner em uso

Confira os contêineres existentes:

```powershell
docker ps -a
```

Para reutilizar o contêiner parado:

```powershell
docker start simpi-backend
```

Para substituí-lo pela imagem atual, use os comandos de parada,
remoção e execução da seção de reconstrução.

## Testes

Todos os comandos abaixo partem da raiz do repositório.
Os comandos Maven exigem JDK 21 instalado na máquina.

Testes unitários, sem banco:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml test
```

Para integração local, inicie o PostgreSQL exclusivo de testes:

```powershell
docker compose --profile integration up -d --wait postgres-test
```

Esse serviço usa simpi_test na porta local 5433 e o volume próprio
postgres_test_data. Ele é separado do banco de desenvolvimento
simpi_dev. Não há rotinas de exclusão ou limpeza de dados.

Execute o Maven em um terminal PowerShell exclusivo para testes,
usando os valores locais correspondentes ao .env:

```powershell
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5433/simpi_test"
$env:POSTGRES_USER = "simpi_test"
$env:POSTGRES_PASSWORD = "simpi_test_only"
.\backend\mvnw.cmd -f backend/pom.xml verify -Pintegration
```

A senha deve acompanhar qualquer personalização de
POSTGRES_TEST_PASSWORD no .env. Use outro terminal para executar
o backend de desenvolvimento, preservando suas variáveis.

Os testes de integração usam nomes *IT e verificam PostgreSQL
e Flyway. Os unitários usam *Test ou *Tests e não acessam banco.

Atualmente, verify sem o perfil integration compila e empacota
o backend, mas não executa o teste de inicialização reclassificado
como BackendApplicationIT. Ainda não existem testes unitários
de negócio. O comando verify -Pintegration executa também os
testes de integração e exige o PostgreSQL exclusivo de testes.

O build da imagem executa verify sem o perfil integration e não
exige banco externo. O CI ativa esse perfil com um PostgreSQL
temporário, separado dos bancos locais.

Flyway será responsável pelas futuras migrations em
backend/src/main/resources/db/migration. Hibernate apenas valida
o esquema, sem criar ou alterar tabelas. Ainda não há entidades,
migrations ou consultas de negócio; nesta etapa a integração
verifica inicialização, SELECT 1 e validação Flyway. A tabela
de histórico do próprio Flyway é infraestrutura.
