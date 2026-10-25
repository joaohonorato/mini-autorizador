# Collection do Postman

Testes da API para rodar à mão ou em lote: o roteiro do enunciado, os casos de borda, o bloqueio opcional por senhas erradas, uma carga inicial de cartões e a idempotência por `Idempotency-Key`.

| Arquivo | Para quê |
|---|---|
| `mini-autorizador.postman_collection.json` | A collection, com 5 pastas e os testes (`pm.test`) de cada requisição |
| `mini-autorizador.postman_environment.json` | O ambiente "Mini Autorizador - local" |
| `carga-inicial.csv` | Os 20 cartões da carga inicial, uma linha por iteração do Runner |

## Antes de começar

A aplicação precisa estar rodando em `http://localhost:8080` (veja o [README da raiz](../../README.md#como-executar)). No Postman, use **Import** e selecione a collection e o ambiente, e escolha o ambiente "Mini Autorizador - local".

| Variável | Padrão | Uso |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Endereço da aplicação |
| `apiUsuario` / `apiSenha` | `username` / `password` | Credenciais Basic da API |
| `bloqueioAtivo` | `false` | Diz aos testes da pasta 3 se a aplicação foi iniciada com `BLOQUEIO_SENHA_ATIVO=true` |

## As pastas

| Pasta | O que cobre |
|---|---|
| **1. Roteiro do enunciado** | Criar o cartão, conferir o saldo de 500.00, fazer transações de 60,00 conferindo o saldo depois de cada uma até a recusa por `SALDO_INSUFICIENTE` (8 aprovadas, saldo final 20.00), senha inválida e cartão inexistente |
| **2. Casos de borda** | 401 sem credenciais ou com senha errada, `/error` fechado, número com 12, 15, 17 e 20 dígitos ou com letra (só 16 dígitos são aceitos; no cadastro dá 400, na consulta 404 e na transação `CARTAO_INEXISTENTE`), zeros à esquerda, senha vazia e acima de 72 bytes, número JSON no lugar de texto, `Accept` incompatível (406 sem criar o cartão), valores com 3 casas, zero, negativo e gigante, campos em branco e valor igual ao saldo |
| **3. Bloqueio por senhas erradas** | Três senhas erradas seguidas e uma certa. Com `bloqueioAtivo=false` (padrão), a certa é autorizada; com a aplicação iniciada com `BLOQUEIO_SENHA_ATIVO=true` e `bloqueioAtivo=true`, recebe `422 CARTAO_BLOQUEADO` |
| **4. Carga inicial** | Cadastra os cartões do CSV e aplica as transações indicadas (veja abaixo) |
| **5. Idempotência** | Uma compra com `Idempotency-Key`, o reenvio com a mesma chave (mesma resposta, `Idempotent-Replayed: true`, saldo debitado uma vez só), a mesma chave com outro valor (`422`), uma chave fora do formato (`400`) e a recusa repetida |

As pastas 1 e 4 repetem requisições com `postman.setNextRequest`, o que só funciona no **Collection Runner** ou no **Newman**, não ao enviar uma requisição isolada. Cada execução das pastas 1, 2, 3 e 5 usa números de cartão novos, então pode ser repetida à vontade.

## Carga inicial (Runner)

1. Abra o **Runner**, escolha a collection e marque só a pasta **4. Carga inicial**.
2. Em **Data**, selecione `carga-inicial.csv`. O Runner passa a mostrar 20 iterações.
3. Execute.

Cada iteração cadastra um cartão (`POST /cartoes`), aplica as transações da linha (`POST /transacoes`, a quantidade da coluna `transacoes`, cada uma de `valor`) e confere o saldo final. O resultado esperado:

| Cartões | Senha | Transações | Saldo final |
|---|---|---|---|
| `6549873025634501`, `6000000000000002` a `6000000000000010` | `1234` (o `...04` usa `4321`, o `...05` usa `0000`) | nenhuma | 500.00 |
| `6000000000000011`, `6000000000000012` | `1234` | 1 de 10,00 | 490.00 |
| `6000000000000013` | `4321` | 1 de 99,99 | 400.01 |
| `6000000000000014` | `1234` | 2 de 100,00 | 300.00 |
| `6000000000000015` | `1234` | 4 de 50,00 | 300.00 |
| `6000000000000016` | `1234` | 8 de 60,00 (igual ao roteiro) | 20.00 |
| `6000000000000017` | `1234` | 9 de 55,55 | 0.05 |
| `6000000000000018` | `1234` | 1 de 500,00 | 0.00 |
| `6000000000000019` | `1234` | 5 de 100,00 | 0.00 |
| `6000000000000020` | `1234` | 3 de 166,66 | 0.02 |

A carga é **idempotente**: se o cartão já existe (`422` no cadastro), as transações não são reaplicadas, e o saldo atual aparece no console do Postman. Para recomeçar do zero, use um banco novo.

Cartões cujo número começa com zero não estão no CSV: o Newman 6.2.3 converte valores numéricos do arquivo em número e perde os zeros (`0000873025634501` chegaria como `873025634501`). Esse caso é coberto na pasta 2, que gera o número dentro do script.

## Pelo Newman (linha de comando)

```bash
npm install -g newman

# Pastas 1, 2, 3 e 5, com o bloqueio desligado (padrão)
newman run docs/postman/mini-autorizador.postman_collection.json \
  -e docs/postman/mini-autorizador.postman_environment.json \
  --folder "1. Roteiro do enunciado" --folder "2. Casos de borda" \
  --folder "3. Bloqueio por senhas erradas" --folder "5. Idempotência"

# Carga inicial
newman run docs/postman/mini-autorizador.postman_collection.json \
  -e docs/postman/mini-autorizador.postman_environment.json \
  --folder "4. Carga inicial" -d docs/postman/carga-inicial.csv
```

Para testar o bloqueio ligado, inicie a aplicação com `BLOQUEIO_SENHA_ATIVO=true` e acrescente `--env-var bloqueioAtivo=true` ao primeiro comando.

A collection foi verificada com o Newman 6.2.3 contra a aplicação, com o bloqueio desligado e ligado, e os saldos da carga conferidos um a um. A última verificação, com a regra dos 16 dígitos e a pasta 5, passou sem nenhuma falha: 62 requisições e 72 asserts nas pastas 1 a 3, 8 requisições e 12 asserts na pasta 5, e 20 iterações na carga, com o bloqueio desligado; nas pastas 1 a 3 e 5 com o bloqueio ligado, o mesmo resultado.
