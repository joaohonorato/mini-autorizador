# 0007 - Documentação OpenAPI e Swagger UI atrás do Basic Auth

**Status:** revogada. O springdoc, o Swagger UI, o `docs/openapi.json` e o `OpenApiDocumentoTest` foram removidos: o contrato da API é o do enunciado, que já está no repositório, e a dependência, as anotações nos controllers e o teste que mantinha o arquivo em dia custavam mais do que a avaliação ganhava. O texto abaixo fica como registro do que foi decidido antes.

## Contexto

O contrato da API está no enunciado, em texto. Quem avalia ou integra a API ganha tempo com um documento que possa importar no Postman ou no Insomnia e com uma tela para experimentar as rotas. Por outro lado, o contrato de uma API de pagamentos não precisa ficar publicado na própria aplicação em produção.

## Decisão

- **O springdoc-openapi gera o documento** a partir das anotações dos controllers e dos DTOs (`@Operation`, `@ApiResponse`, `@Schema`). A `OpenApiConfig` cuida do que vale para a API inteira: o título, o esquema Basic e a resposta 401 de todas as rotas.
- **O Swagger UI (`/swagger-ui.html`) e o `/v3/api-docs` passam pelo mesmo Basic Auth da API.** O navegador pede as credenciais ao abrir a tela, e nenhuma rota nova é pública.
- **`DOCUMENTACAO_ATIVA=false` desliga os dois.** O padrão é ligado, para facilitar a avaliação.
- **`docs/openapi.json` é versionado**, e o `OpenApiDocumentoTest` falha se ele divergir do documento que a aplicação gera. Para atualizá-lo depois de uma mudança intencional: `./mvnw test -Dtest=OpenApiDocumentoTest -Dopenapi.atualizar=true`.

## Alternativas consideradas

- **Escrever o OpenAPI à mão.** Não traz dependência nova, mas o arquivo se afasta do código sem ninguém perceber. Gerar do código e conferir o arquivo versionado num teste evita isso.
- **Publicar o Swagger UI sem autenticação.** É mais cômodo, mas abriria uma rota pública fora do contrato. Manter tudo atrás do Basic Auth preserva a regra de que nada responde sem credenciais.
- **Não documentar a API.** O enunciado já define o contrato, mas um documento executável encurta a avaliação e serve de fonte para clientes.

## Consequências

- O `springdoc-openapi-starter-webmvc-ui` entra como dependência da aplicação.
- Controllers e DTOs ficam com anotações de documentação, o que os deixa mais longos.
- Em produção, o Swagger UI exposto é uma superfície a mais; o README sugere desligá-lo com `DOCUMENTACAO_ATIVA=false`.
