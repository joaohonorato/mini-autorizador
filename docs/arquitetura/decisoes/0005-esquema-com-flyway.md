# 0005 - Esquema do banco versionado com Flyway

**Status:** aceita

## Contexto

A primeira versão criava a tabela na inicialização com um `schema.sql` (`CREATE TABLE IF NOT EXISTS`). Esse script cria a tabela num banco novo, mas não evolui uma tabela que já existe. Quando o bloqueio por senhas erradas exigiu duas colunas novas, um banco criado antes, como o container que alguém já estivesse usando, não as receberia.

## Decisão

O esquema é versionado com Flyway, em `src/main/resources/db/migration`:

- `V1__cria_tabela_cartao.sql`: a tabela original.
- `V2__adiciona_bloqueio_por_senha.sql`: as colunas `tentativas_senha` e `bloqueado_ate`.

`spring.flyway.baseline-on-migrate=true` com `baseline-version=1` trata o banco criado antes do Flyway: ele já tem a tabela, que corresponde à versão 1, então é marcado como versão 1 e recebe só as migrações novas.

## Alternativas consideradas

- **Manter o `schema.sql` e pedir para recriar o banco** (`docker compose down -v`). Perde os cartões e exige um passo manual.
- **`ddl-auto=update` do Hibernate.** Evolui o esquema sem controle de versão e sem garantia de que o resultado é o esperado.

## Consequências

- Foi verificado com um MySQL 5.7 isolado: uma tabela no formato antigo, com um cartão de saldo 123,45, foi reconhecida como versão 1 e recebeu só a V2; o saldo permaneceu 123,45 e as colunas novas entraram zeradas. Num banco vazio, as duas migrações rodam em sequência.
- Uma migração já aplicada não pode ser editada: o Flyway compara o checksum e recusa. Mudanças de esquema passam a ser migrações novas.
- Acrescenta duas dependências (`flyway-core` e `flyway-mysql`).
