# Regras do projeto SIMPI

- Trabalhe somente neste repositório.
- Antes de executar comandos ou editar arquivos, explique o plano e
  aguarde autorização explícita. Execute apenas o escopo autorizado.
- Commit, push e exclusões exigem autorização específica.
- Preserve alterações existentes do usuário.
- Exclua .git e backend/target das buscas e listagens, salvo solicitação.
- Economize tokens: faça buscas direcionadas, leia apenas trechos
  necessários e resuma resultados sem reproduzir saídas extensas.
- Evite repetir verificações sem motivo e execute somente os testes
  relevantes e autorizados.
- Ao concluir, informe mudanças, verificações e pendências brevemente.

## Qualidade e arquitetura do backend

- Aplicar Clean Code e SOLID proporcionalmente ao escopo.
- Separar HTTP, regras de negócio e persistência.
- Usar injeção por construtor e responsabilidades coesas.
- Criar interfaces e abstrações quando houver necessidade concreta.
- Retornar DTOs e preservar os contratos aprovados.
- Proteger transições de estado e delimitar transações nos casos de uso.
- Evoluir o esquema por Flyway; Hibernate somente valida.
- Testar regras, permissões, concorrência e rollback conforme o risco,
  respeitando a autorização prévia para executar testes.

## Idioma e identificadores

- Usar português do Brasil nos identificadores definidos pela equipe,
  incluindo classes, DTOs, métodos, atributos, variáveis, pacotes,
  tabelas, colunas e testes.
- Exceção explícita: manter o domínio em português e usar os sufixos
  técnicos convencionais Controller, Service, Repository, Request,
  Response e Mapper, como SolicitacaoRepository e CriarSolicitacaoRequest.
  Entidades, métodos, atributos e variáveis continuam em português.
  Essa convenção não exige criar camadas sem necessidade concreta.
- Escrever identificadores sem acentos ou cedilha, respeitando as
  convenções da linguagem. Usar acentuação normal em mensagens
  e documentação.
- Preservar identificadores obrigatórios das tecnologias e os nomes
  já definidos nos contratos da API.
- Não renomear código existente automaticamente.

## Documentação funcional

- Não inventar nem reconstruir documentos funcionais ausentes.
- Quando faltar uma definição necessária à implementação, registrar
  a lacuna e solicitar a definição ou o documento aprovado.
