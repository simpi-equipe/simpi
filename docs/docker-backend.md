# Backend com Docker

## Pré-requisitos

- Docker instalado e em execução, com suporte a contêineres Linux.
- Acesso à internet para baixar imagens e dependências.
- Execute todos os comandos abaixo na raiz do repositório.

Java e Maven locais não são necessários: a imagem usa Java 21
e o Maven Wrapper para compilar e verificar o projeto.

## Construir a imagem

```powershell
docker build -t simpi-backend:local ./backend
```

O contexto de construção é `backend`. O `.dockerignore` exclui,
entre outros arquivos, `target/`, logs e arquivos `.env`.

## Executar

Se houver um contêiner da validação anterior criado com `--rm`,
pare-o antes; ele será removido automaticamente. Depois execute
o comando abaixo para criar o contêiner persistente.

```powershell
docker run -d --name simpi-backend -p 127.0.0.1:8080:8080 simpi-backend:local
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
docker run -d --name simpi-backend -p 127.0.0.1:8080:8080 simpi-backend:local
```

A remoção acima exclui o contêiner anterior. Apenas reiniciá-lo
não aplica a nova imagem.

## Problemas comuns

### Porta 8080 ocupada

Pare o serviço que ocupa a porta ou publique em outra porta local:

```powershell
docker run -d --name simpi-backend -p 127.0.0.1:8081:8080 simpi-backend:local
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
