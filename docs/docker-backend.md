# Execução local e testes do backend

Este guia descreve como executar o código do checkout. A presença dos arquivos
não comprova integração à main, aprovação do CI ou disponibilidade em deploy.
Consulte o [estado do código documentado](documentacao-funcional.md#estado-do-código-documentado)
e o [contrato v1.1](contratos-api-v1.1.md) para escopo e regras.

## Pré-requisitos

- Execute os comandos a partir da raiz do repositório.
- Docker Desktop em execução, com contêineres Linux e Docker Compose.
- Acesso à internet para baixar imagens e dependências.
- JDK 21 para IntelliJ, Maven local ou JShell. A execução inteiramente por Docker
  usa o JDK da imagem de build e não exige Java/Maven instalados no Windows.
- Os exemplos de entrada com `Read-Host -MaskInput` exigem PowerShell 7.

O Compose atual define somente os serviços de banco. O backend em contêiner
é construído pelo [Dockerfile](../backend/Dockerfile) e iniciado com `docker run`.

## Preparar o ambiente

Copie o [modelo](../.env.example) somente se o arquivo local ainda não existir:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Revise o arquivo local sem publicar seus valores. Senhas, chaves, tokens e hashes
não devem ser commitados. As variáveis têm as seguintes responsabilidades:

| Variável | Uso |
| --- | --- |
| POSTGRES_DB | Nome do banco de desenvolvimento criado pelo serviço postgres |
| POSTGRES_USER / POSTGRES_PASSWORD | Conta do PostgreSQL; também usadas pelo backend |
| POSTGRES_PORT | Porta publicada no Windows; padrão 5432 |
| POSTGRES_TEST_PASSWORD | Senha exclusiva do serviço postgres-test; mantenha definida no ambiente do Compose |
| DB_URL | JDBC do banco que o backend deve usar; depende do modo de execução |
| JWT_SECRET | Chave própria, Base64 de pelo menos 32 bytes aleatórios |
| CORS_ORIGENS_PERMITIDAS | Origens exatas do frontend separadas por vírgula; substitui a lista local no ambiente publicado |

Spring Boot não lê `.env` automaticamente. O Compose o usa para interpolação;
o backend em contêiner recebe as variáveis por `--env-file`. No IntelliJ ou em
Java local, configure o ambiente do processo explicitamente.

As credenciais e o nome do PostgreSQL são aplicados pela imagem quando o volume
está vazio. Alterar o `.env` não reconfigura um banco já inicializado. Preserve
os valores compatíveis com o volume existente; não remova volumes para corrigir
credenciais ou conflitos de porta.

## Configurar JWT

Substitua o placeholder de JWT_SECRET por uma chave própria. Para gerar Base64
de 32 bytes aleatórios no PowerShell, quando for preparar seu ambiente:

```powershell
$chaveJwt = New-Object byte[] 32
$gerador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $gerador.GetBytes($chaveJwt)
    [Convert]::ToBase64String($chaveJwt)
} finally {
    $gerador.Dispose()
}
```

Copie o resultado apenas para o ambiente local. Não há chave operacional neste
guia nem fallback de produção. Não reutilize chaves públicas de fixtures/CI,
não use texto arbitrário como Base64 e não reutilize a chave local em produção.
Uma chave ausente, inválida ou curta impede a inicialização da autenticação.

A configuração `jwt.expiration` está em milissegundos; o padrão em
[application.properties](../backend/src/main/resources/application.properties)
é `3600000`, equivalente a 60 minutos. O campo `expiresIn` do login está em
segundos: `3600` no padrão. É prazo desde a emissão, não de inatividade.
JWT_EXPIRATION pode sobrescrever essa propriedade no ambiente do backend;
a mesma unidade de milissegundos se aplica.

## Bancos e portas

| Uso | Destino do cliente | Banco |
| --- | --- | --- |
| Backend em Docker | postgres:5432, na rede simpi-local_default | Valor local de POSTGRES_DB |
| IntelliJ/Java no Windows | 127.0.0.1 e POSTGRES_PORT, padrão 5432 | O mesmo banco de desenvolvimento |
| Integração no Windows | 127.0.0.1:5433 | Exclusivamente simpi_test |
| API no Windows | http://127.0.0.1:8080 | — |

O [Compose](../compose.yaml) publica os bancos apenas em loopback e mantém
volumes separados: postgres_data e postgres_test_data. Dentro da rede Docker,
o PostgreSQL continua na porta 5432 mesmo quando POSTGRES_PORT muda no Windows.
Não aponte integração para o banco de desenvolvimento.

Para iniciar o banco e aguardar o healthcheck:

```powershell
docker compose up -d --wait postgres
```

## Banco em Docker e backend no IntelliJ

1. Prepare o ambiente e a chave JWT; inicie o serviço postgres.
2. Abra o módulo backend no IntelliJ, use JDK 21 e carregue o projeto Maven.
3. Na Run Configuration de BackendApplication, defina DB_URL, POSTGRES_USER,
   POSTGRES_PASSWORD e JWT_SECRET com seus valores locais.
4. Para DB_URL use `jdbc:postgresql://127.0.0.1:<porta-publicada>/<banco-local>`.
   Não use o hostname postgres para o processo Java no Windows.
5. Execute BackendApplication e aguarde a mensagem `Started BackendApplication`.

O IntelliJ não herda automaticamente o `.env` nem as variáveis definidas em
outro terminal. Reinicie o processo Java após alterar sua Run Configuration.

Como alternativa ao IntelliJ, em PowerShell 7, ajustando porta e banco:

```powershell
$env:DB_URL = "jdbc:postgresql://127.0.0.1:<porta-publicada>/<banco-local>"
$env:POSTGRES_USER = Read-Host "Usuário do banco local"
$env:POSTGRES_PASSWORD = Read-Host -MaskInput "Senha do banco local"
$env:JWT_SECRET = Read-Host -MaskInput "Chave Base64 própria"
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
```

Execute apenas uma instância do backend na porta 8080.

## Banco e backend em Docker

No `.env` usado pelo contêiner, DB_URL deve apontar para
`jdbc:postgresql://postgres:5432/<banco-local>`, coerente com POSTGRES_DB.
Prepare a chave JWT e inicie o banco antes do backend.

```powershell
docker build --pull -t simpi-backend:local ./backend
docker run -d --name simpi-backend --network simpi-local_default --env-file .env -p 127.0.0.1:8080:8080 simpi-backend:local
```

O contexto é backend. O [.dockerignore](../backend/.dockerignore) exclui target,
logs, arquivos de IDE e `.env`. O build executa Maven `verify` sem o perfil
integration: roda testes unitários/MVC/OpenAPI, mas não testes `*IT` nem banco externo.

O Flyway aplica migrations pendentes na inicialização; Hibernate somente valida
o esquema. Migrations não fornecem usuários de demonstração. Há um
[seed optativo de desenvolvimento](seed-desenvolvimento.md), executado pelo backend
no host ou em um contêiner temporário na rede do PostgreSQL local.

### Mitigação temporária da CVE-2026-114938

Em 17/11/2026, o [roteiro da Adoptium](https://adoptium.net/support) indicava
`21.0.13.1` como a próxima atualização do Temurin 21. O
[catálogo oficial de imagens](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin)
listava `21.0.13_9-jdk-jammy` e `21.0.13_9-jre-jammy`. No Docker Hub, as tags
genéricas `21-jdk-jammy` e `21-jre-jammy` apontavam para os mesmos manifestos
das respectivas tags versionadas.

O Dockerfile fixa essas duas tags em `21.0.13+9` e desativa os intrínsecos de
Base64 durante o build Maven e na JVM do backend. Essa é uma mitigação até
que existam tags oficiais com a correção confirmada para JDK e JRE.
Referências: [atualização do Java 21 da Oracle](https://www.oracle.com/java/technologies/javase/21-0-13-1-relnotes.html)
e [orientação de mitigação do Google Cloud](https://docs.cloud.google.com/apigee/docs/hybrid/v1.17/mitigate-cve-2026-114938).

Para reconstruir e conferir a JVM dos dois estágios em contêineres temporários,
sem rede, portas publicadas ou acesso ao PostgreSQL:

```powershell
docker build --pull --progress=plain -t simpi-backend:cve-2026-114938-local ./backend
docker build --pull --target build -t simpi-backend-build:cve-2026-114938-local ./backend
docker run --rm --network none --entrypoint java simpi-backend-build:cve-2026-114938-local -version
docker run --rm --network none --entrypoint java simpi-backend:cve-2026-114938-local -version

docker run --rm --network none --entrypoint java simpi-backend-build:cve-2026-114938-local -XX:+PrintFlagsFinal -version | Select-String UseBASE64Intrinsics
docker image inspect simpi-backend:cve-2026-114938-local --format '{{json .Config.Entrypoint}}'
docker run --rm --network none --entrypoint java simpi-backend:cve-2026-114938-local -XX:+UnlockDiagnosticVMOptions -XX:-UseBASE64Intrinsics -XX:+PrintFlagsFinal -version | Select-String UseBASE64Intrinsics
```

Confira `21.0.13+9`, `UseBASE64Intrinsics = false` e as opções da JVM no
`Entrypoint` da imagem final. Quando a Adoptium publicar as imagens corrigidas,
confirme as tags e o build de ambos os estágios, substitua as bases, retire a
mitigação temporária e repita o build e a verificação da versão.

### Logs, parada e reinício

```powershell
docker logs -f simpi-backend
```

Aguarde `Started BackendApplication`. Ctrl+C encerra apenas o acompanhamento.
A raiz / não é um endpoint de negócio; seu status isolado não comprova
inicialização nem disponibilidade das consultas autenticadas.

Para parar e retomar mantendo contêineres e volumes:

```powershell
docker stop simpi-backend
docker compose stop postgres

docker compose up -d --wait postgres
docker start simpi-backend
```

### Recriar após mudar código ou variáveis

Se o código ou as dependências mudaram, reconstrua a imagem. Se somente o
`.env` do backend mudou, reutilize a imagem e recrie apenas o contêiner.

Para um contêiner existente que será substituído:

```powershell
docker stop simpi-backend
docker rm simpi-backend
docker run -d --name simpi-backend --network simpi-local_default --env-file .env -p 127.0.0.1:8080:8080 simpi-backend:local
```

Essa remoção afeta somente o contêiner do backend, não os volumes do banco.
`docker start` não adota uma nova imagem nem relê o `.env`.
Se precisar reconstruir, execute o `docker build` mostrado acima antes da recriação.

### Publicação futura no Render

- Antes do push, confirme a branch vinculada, o `Auto-Deploy`, o caminho do
  Dockerfile e o contexto de build do serviço. O build precisa usar
  `backend/Dockerfile` com contexto `backend`. Com a raiz do serviço em `backend`,
  o caminho do Dockerfile é `./Dockerfile`. Um serviço Docker vinculado ao Git
  pode iniciar um deploy ao receber um push na branch configurada.
  [Docker no Render](https://render.com/docs/docker), [deploy automático](https://render.com/docs/deploys#automatic-deploys).
- Revise eventuais comandos de inicialização personalizados e preserve os
  valores existentes de `DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`,
  `JWT_SECRET`, `JWT_EXPIRATION` e `CORS_ORIGENS_PERMITIDAS`, além do perfil
  Spring, da porta e do health check já configurados no serviço.
- Confira o histórico `flyway_schema_history` do banco de destino em modo
  somente leitura antes de publicar. As migrações PostgreSQL deste checkout são
  `V1` (fundação patrimonial) e `V2` (solicitações e movimentação). Esta mudança
  não inclui migrações. O Flyway aplica as que estiverem pendentes ao iniciar
  o backend, então registre o resultado da conferência antes de aprovar o push.
- Após o deploy autorizado, confirme a versão do Java nos logs de inicialização.
  Use uma conta de teste existente em `POST /api/auth/login` e consulte
  `GET /api/bens` e `GET /api/solicitacoes` com o token recebido, sem registrar
  credenciais ou tokens.

## Integração com o frontend e CORS

O backend trata CORS em `/api/**`, antes da autenticação. Sem configuração
adicional, permite `http://localhost:5173` e `http://127.0.0.1:5173`, usados
pelo Vite local. Porta, protocolo e hostname fazem parte da origem; uma porta
diferente exige atualizar a lista.

Para o ambiente publicado, defina `CORS_ORIGENS_PERMITIDAS` no processo do
backend com as URLs reais do frontend, separadas por vírgula. Exemplo fictício:

```dotenv
CORS_ORIGENS_PERMITIDAS=https://frontend.simpi.example,https://outro.simpi.example
```

Essa lista substitui as origens locais; inclua-as explicitamente se também
precisar delas naquele ambiente. Use origens sem caminho, query ou barra final,
com protocolo e porta quando aplicável. Use endereços exatos, sem `*`.
Uma lista vazia não libera requisições entre origens diferentes.

- Métodos permitidos: `GET`, `POST` e `OPTIONS`.
- Cabeçalhos de requisição permitidos: `Authorization` e `Content-Type`.
- `Location` é exposto para o frontend ler a URL de uma solicitação criada.
- O preflight válido é atendido sem JWT; as operações protegidas continuam
  exigindo `Authorization: Bearer <token>` e respeitando as permissões existentes.
- Cookies e credenciais automáticas do navegador não são habilitados. O token
  continua em memória do frontend, conforme o contrato do MVP.

No Docker, atualize o `.env` usado por `--env-file` e recrie o contêiner para
reler as variáveis. Na primeira aplicação desta mudança, reconstrua também a
imagem, conforme [a recriação após alterações](#recriar-após-mudar-código-ou-variáveis).
No IntelliJ/Java local, configure a variável na execução e reinicie o processo.
O Swagger na mesma origem e os clientes sem `Origin`, como Insomnia, continuam
usando a autenticação normal.

Os testes `ConfiguracaoCorsTest` e `ConfiguracaoCorsPublicacaoTest` exercitam a
cadeia real de segurança, os preflights, as listas de origens, o bloqueio de
métodos/cabeçalhos, o `401` com CORS e a leitura de `Location`, sem banco externo.

Em 09/11/2026, passaram 141 testes selecionados nessas duas classes e em
`ContratoAutenticacaoTest`, `ContratoOpenApiTest`,
`TratamentoErrosControllerAdviceTest` e `SolicitacaoExecucaoControllerTest`,
sem falhas, erros ou casos ignorados, com `BUILD SUCCESS`. Essa execução não
inclui integração com PostgreSQL nem comprova atualização do contêiner em execução.

Na reconstrução Docker de 09/11/2026, passaram os 269 testes unitários/MVC/OpenAPI
executados pelo Dockerfile, sem falhas, erros ou casos ignorados, com `BUILD SUCCESS`.
Após recriar o backend local com essa imagem, a validação HTTP confirmou preflight
`200` para as duas origens locais, `403` para uma origem fora da lista e `401`
em `/api/bens` sem JWT, com os cabeçalhos CORS e exposição de `Location` presentes.
Nenhum registro de negócio foi alterado por essas chamadas. Essa validação local
não comprova publicação remota nem execução dos testes `*IT` no build Docker.

## Swagger e OpenAPI

Com o backend iniciado, acesse:

- Interface: http://127.0.0.1:8080/swagger-ui/index.html
- Documento JSON: http://127.0.0.1:8080/v3/api-docs

1. Execute POST /api/auth/login com e-mail e senha de um usuário ativo existente.
2. Copie somente accessToken da resposta.
3. Em Authorize, cole o token sem escrever Bearer; o esquema HTTP Bearer inclui o prefixo.
4. Confira GET /api/auth/me e execute as consultas desejadas.

O login é público. /me, as oito consultas, a criação e todas as ações de solicitações exigem Bearer; os endereços da
documentação já estão liberados na configuração de segurança. O principal
autenticado não é um parâmetro ou corpo de entrada. Services continuam
recarregando e revalidando o usuário no banco.

Em 401, obtenha um novo token quando aplicável. Em 403, confira perfil e
permissões; repetir o login com o mesmo perfil não concede acesso.
Parâmetros, padrões, DTOs e códigos públicos seguem o
[contrato](contratos-api-v1.1.md). A criação retorna 201 com Location;
aprovação, recusa, cancelamento, envio e recebimento retornam 200 com o pedido atualizado.
Envio e recebimento não exigem corpo; veja o [roteiro das ações](acoes-solicitacoes.md).

## Coleção Insomnia

Importe por Import → File a
[coleção de consultas, criação e ações](testes-manuais/insomnia/simpi-consultas.json).
Ela contém 51 requisições em sete pastas numeradas.

No ambiente base, ajuste url_base e preencha email/senha com uma conta local.
Execute o login e copie accessToken para token, sem Bearer. A captura do token
é manual. Nas consultas seguintes, preencha setorId, localizacaoId, bemId e
solicitacaoId com IDs realmente retornados pelo seu banco.
Para a pasta de criação, preencha também setorDestinoId, localizacaoDestinoId
e responsavelDestinoId. O POST cria dados; sua repetição para o mesmo bem
retorna 409 enquanto houver solicitação em andamento.
Na pasta de ações, copie aprovacaoId e segundaAprovacaoId do pedido. Troque
o token quando trocar de ator. O pedido aprovado pode seguir para envio e
recebimento; use pedidos distintos para recusa, cancelamento e recebimento
antecipado, conforme o [roteiro de ações](acoes-solicitacoes.md).

Siga as descrições e a ordem das pastas. Casos de GESTOR/ADMINISTRADOR exigem
contas desses perfis e tokens correspondentes; não execute toda a coleção
como se todos os cenários esperassem sucesso para o mesmo usuário.
O exemplo idInexistente deve ser confirmado como ausente no banco.

## Preparar dados locais

Não há cadastro público ou conta habilitada por padrão. Um banco migrado pode
não ter usuários; o login exige uma carga local explícita. Para dados fictícios
reproduzíveis, use o [seed de desenvolvimento](seed-desenvolvimento.md).
O preparo manual abaixo permanece disponível para cadastros próprios.
Não use fixtures de integração como rotina de carga no banco de desenvolvimento.

A [preparação aprovada do ciclo setor/gestor](documentacao-funcional.md#preparação-do-ciclo-setor-e-gestor)
continua sendo a referência. Para um banco sem cadastros, o mínimo para testar
um COMUM é um setor, um GESTOR de apoio do próprio setor e o usuário COMUM.
Se já existir setor válido, avalie seu reaproveitamento antes de criar outro.

Antes de qualquer inserção, confirme localmente o destino do backend sem imprimir
credenciais, identifique banco/schema e verifique migrations, código do setor e
e-mails pretendidos. Use um cliente conectado ao serviço postgres de desenvolvimento,
não ao postgres-test. Consultas iniciais, sem senhas ou hashes:

```sql
SELECT current_database(), current_schema();
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, codigo, gestor_id, ativo FROM setor ORDER BY id;
```

Gere hashes com o `BCryptPasswordEncoder` usado pela aplicação. Sem alterar o
projeto, obtenha o classpath com o Maven e abra JShell usando JDK 21:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml dependency:build-classpath "-DincludeScope=runtime"
jshell --class-path "<classpath informado pelo Maven>"
```

No JShell, use `/set feedback silent` para evitar eco automático de valores e
`new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()`:
gere `encode(senhaLocal)` e confirme `matches(senhaLocal, hashGerado)` antes da carga.
Use uma senha de desenvolvimento escolhida localmente para o COMUM e uma senha
aleatória para o gestor de apoio; não exiba nem versione esses valores.
Hashes sintéticos das fixtures não são credenciais de login garantidas.

O modelo abaixo é para psql. Defina previamente, somente na sessão local, as
variáveis codigo_setor, nome_setor, nome_gestor, email_gestor, hash_gestor,
nome_usuario, email_usuario e hash_usuario com os valores locais preparados.
Não substitua os parâmetros por dados operacionais em arquivos versionados.
As variáveis hash_gestor/hash_usuario devem conter hashes BCrypt verificados.

```sql
\set ON_ERROR_STOP on
BEGIN;

WITH identificadores AS (
    SELECT nextval('sequencia_setor') AS setor_id,
           nextval('sequencia_usuario') AS gestor_id
),
novo_setor AS (
    INSERT INTO setor (id, codigo, nome, gestor_id, ativo)
    SELECT setor_id, :'codigo_setor', :'nome_setor', gestor_id, TRUE
    FROM identificadores
    RETURNING id, gestor_id
),
novo_gestor AS (
    INSERT INTO usuario (id, nome, email, senha_hash, perfil, setor_id, ativo)
    SELECT gestor_id, :'nome_gestor', :'email_gestor', :'hash_gestor',
           'GESTOR', id, TRUE
    FROM novo_setor
    RETURNING setor_id
)
INSERT INTO usuario (nome, email, senha_hash, perfil, setor_id, ativo)
SELECT :'nome_usuario', :'email_usuario', :'hash_usuario', 'COMUM', setor_id, TRUE
FROM novo_gestor
RETURNING id, perfil, setor_id, ativo;

COMMIT;
```

Os IDs vêm das sequências existentes; o COMUM usa o default de usuario.id.
A FK setor.gestor_id permanece obrigatória e é verificada no commit.
Perfil GESTOR e pertencimento ao setor precisam ser respeitados também na escrita
SQL, pois a FK sozinha não garante essas condições. Em qualquer erro, interrompa,
encerre/reverta a transação e diagnostique antes de repetir; não repita a carga
automaticamente. A carga não altera o esquema ou registros existentes.

Após o commit, confira apenas IDs, nomes, perfis, vínculos e atividade. Valide o
login da conta COMUM no Swagger e confira /me com o token. Sem bens ou pedidos
preparados, listas vazias são válidas; detalhes e históricos com sucesso exigem
dados adicionais, fora dessa carga mínima.

## Testes

Os comandos Maven locais exigem JDK 21. Testes unitários, MVC e OpenAPI usam
`*Test`/`*Tests` e não exigem banco externo. Os testes dos Controllers de consulta
e do OpenAPI usam services mockados e a cadeia real de segurança; os testes do
advice também incluem cenários standalone. Testes `*IT` dependem do perfil integration.

```powershell
.\backend\mvnw.cmd -f backend/pom.xml test
.\backend\mvnw.cmd -f backend/pom.xml verify
```

Para integração, prepare o serviço exclusivo e, em outro terminal PowerShell 7,
configure o banco de testes e uma chave JWT própria:

```powershell
docker compose --profile integration up -d --wait postgres-test
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5433/simpi_test"
$env:POSTGRES_USER = "simpi_test"
$env:POSTGRES_PASSWORD = Read-Host -MaskInput "Senha definida em POSTGRES_TEST_PASSWORD"
$env:JWT_SECRET = Read-Host -MaskInput "Chave Base64 própria para o processo de teste"
.\backend\mvnw.cmd -f backend/pom.xml verify -Pintegration
```

Use terminais separados para desenvolvimento e integração. O serviço de testes
usa volume próprio; a senha deve acompanhar sua configuração local. A proteção
dos testes aceita somente PostgreSQL local, banco exatamente simpi_test e os
formatos de URL aprovados. URLs alternativas de Flyway/Hikari/JNDI, parâmetros,
credenciais na URL e múltiplos hosts são bloqueados. Execução remota exige revisão
explícita dessa proteção. Fixtures são sintéticas; alguns cenários comprovam commit,
outros usam rollback. Não há limpeza automática de registros preexistentes.

No Windows, se for necessário executar Maven pelo Git Bash, substitua a chamada
do wrapper por `& 'C:\Program Files\Git\bin\bash.exe' -lc 'cd backend && ./mvnw test'`
ou pelo objetivo Maven correspondente. Ajuste o caminho se o Git estiver instalado
em outro local. Em ambientes que exigem certificados do Windows, as opções JVM
`-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL` podem ser
acrescentadas a MAVEN_OPTS somente nesse processo, preservando e restaurando o valor
anterior. Não desative TLS nem a validação de certificados.

### Validação manual e evidências

Testes automatizados verificam cenários codificados; não demonstram um deploy.
Os [resultados históricos](documentacao-funcional.md#evidências-de-validação)
identificam entregas e limitações, sem garantir execuções futuras.

Nos testes manuais, registre o status e o corpo realmente recebidos. Um campo
bloqueado pelo Swagger antes do envio não demonstra resposta HTTP 400:
use um cliente que envie o valor inválido, como o Insomnia, para exercitar o backend.
Diferencie array/página vazia de recurso inexistente. Não declare sucesso de
detalhes/históricos quando faltarem dados para essas chamadas.
Não registre credenciais, hashes ou tokens como evidência.

## Problemas comuns

- Porta 8080 ocupada: pare a execução concorrente no IntelliJ ou publique o
  contêiner em outra porta, por exemplo `-p 127.0.0.1:8081:8080`, ajustando a URL dos clientes.
- Nome simpi-backend em uso: confira `docker ps -a`; reutilize o contêiner somente
  se imagem e variáveis ainda forem as desejadas. Caso contrário, siga a recriação.
- Banco em outra porta no Windows: ajuste POSTGRES_PORT e a JDBC local;
  no backend em Docker, mantenha postgres:5432.
- Falha JWT na inicialização: confira presença, Base64 e tamanho da chave própria.
- Login em banco vazio: siga a preparação local, sem criar autenticação provisória.
- Supabase: a versão e os requisitos do futuro PostgreSQL remoto ainda dependem
  de conferência específica; este guia trata do ambiente local.
