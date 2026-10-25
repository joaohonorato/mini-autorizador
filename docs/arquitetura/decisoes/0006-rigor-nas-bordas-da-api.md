# 0006 - Rigor nas bordas da API

**Status:** aceita

## Contexto

O contrato do enunciado define poucos status e formatos. Fora do caminho feliz, a API ainda recebe cabeçalhos, tipos JSON e valores que o contrato não menciona. O que ela faz com eles não pode criar efeito colateral nem expor detalhes internos.

## Decisão

- **`Accept`.** `POST /cartoes` e `GET /cartoes/{numero}` respondem só JSON (`produces`): `application/json`, `*/*` ou nenhum `Accept` funcionam, e qualquer outro tipo recebe 406 antes de o caso de uso rodar. Assim, um erro de negociação nunca deixa um cartão criado. `POST /transacoes` responde o texto `OK` do contrato, então aceita também `text/plain`.
- **Campos de texto só aceitam texto JSON.** `numeroCartao`, `senha` e `senhaCartao` enviados como número ou booleano recebem 400 (`JacksonConfig`). A conversão para texto faria o número do cartão perder os zeros à esquerda sem aviso.
- **Limites de valor.** O valor da transação tem no máximo duas casas decimais e 17 dígitos inteiros (o que cabe em `DECIMAL(19, 2)`). O limite é verificado antes de qualquer cálculo: um valor como `1e40000000` tem representação curta, mas normalizá-lo exigiria montar um número com milhões de dígitos.
- **Senha de até 72 bytes.** O BCrypt só considera os primeiros 72 bytes; sem limite, uma senha errada que só diferisse depois do 72º byte seria aceita. O cadastro rejeita senhas maiores com 400.
- **`/error`.** O servidor encaminha as respostas de erro para `/error` internamente; só esse encaminhamento é liberado, por tipo de despacho. Quem acessa `/error` diretamente recebe 401 sem credenciais e 403 com elas.
- **Login exato.** O usuário da API é comparado com maiúsculas e minúsculas, como a senha.
- **Campo em branco na transação** chega às regras de autorização e é recusado como `CARTAO_INEXISTENTE` ou `SENHA_INVALIDA`; só campo nulo ou ausente vira 400.
- **Número do cartão com exatamente 16 dígitos.** `NumeroCartao` aceita só 16 dígitos numéricos, como o exemplo do enunciado, e não valida o dígito verificador (Luhn), porque o próprio exemplo do enunciado não o satisfaz. No cadastro, um número fora do formato é 400; na consulta e na transação, ele não pode corresponder a nenhum cartão cadastrado, então vira 404 e `CARTAO_INEXISTENTE`.
- **Cadastro responde com `senha` antes de `numeroCartao`.** É a ordem do corpo no enunciado. `@JsonPropertyOrder` a fixa, e o teste de contrato compara o texto exato da resposta.
- **Respostas 400 dizem o que falhou.** O `detail` do `ProblemDetail` aponta o campo, por exemplo `Campos inválidos: valor deve ser maior que zero`, `Campo senha com tipo ou valor inválido` ou `Corpo da requisição ausente ou malformado`. A mensagem sai de um mapa das restrições do Bean Validation, sem expor detalhes internos.

## Alternativas consideradas

- **Ignorar o `Accept` em todas as rotas.** Mais permissivo, e nenhum cliente receberia 406. Mas aceita cabeçalhos que a API não deveria atender.
- **Exigir JSON em todas as rotas, inclusive `/transacoes`.** O corpo `OK` do contrato é texto, e um cliente com `Accept: application/json` receberia 406 no roteiro do avaliador.

## Consequências

- Cada regra tem teste: o `ContratoApiTest` cobre `Accept`, tipos JSON, acesso a `/error`, login em maiúsculas, a ordem dos campos e o detalhe dos 400; o `NumeroCartaoTest` e o `ValidacaoHttpIT` cobrem o formato do número pelo domínio e pelo HTTP real; o `DinheiroTest` e o `BCryptCifradorSenhaTest` cobrem os limites.
- Um cliente com um `Accept` fora do esperado recebe 406 nas rotas de cartão. Curl, Postman e `RestTemplate` mandam `*/*` ou `application/json`, então o roteiro do enunciado não é afetado.
- Quem cadastrava cartões de 13 a 15 ou de 17 a 19 dígitos passa a receber 400: a regra de 16 dígitos é mais estrita que a faixa da ISO/IEC 7812 usada antes.
- `"valor": "10.00"` (texto no lugar de número) ainda é aceito. Esta decisão não cobre o campo `valor`.
