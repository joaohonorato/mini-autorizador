# 0002 - Concorrência resolvida no banco, com update condicional

**Status:** aceita

## Contexto

O enunciado pergunta o que acontece se duas transações de R$ 10,00 chegarem ao mesmo tempo, em instâncias diferentes da aplicação, para um cartão com R$ 10,00. Uma deve ser aprovada e a outra recusada, e o saldo nunca pode ficar negativo. Um lock em memória não resolve, porque cada instância tem a sua memória.

## Decisão

O débito é um `UPDATE` condicional, e o resultado é decidido pelo número de linhas alteradas:

```sql
UPDATE cartao SET saldo = saldo - :valor WHERE numero = :numero AND saldo >= :valor
```

Uma linha alterada significa débito aplicado; zero significa que o saldo não cobria o valor, e a resposta é `SALDO_INSUFICIENTE`. Nunca se lê o saldo para subtrair e gravar depois. O agregado ainda faz uma checagem prévia do saldo, para recusar cedo e manter a regra expressa no domínio, mas a garantia é o `UPDATE`.

O cadastro de cartão segue o mesmo princípio: em vez de consultar e depois inserir, insere e trata a violação da chave primária como "cartão já existente" (`CartaoEntity` implementa `Persistable`, para o `save` não virar um merge que sobrescreveria o cartão).

## Alternativas consideradas

- **Lock pessimista (`SELECT ... FOR UPDATE`).** Manteria a regra inteira no agregado, mas seguraria o lock da linha durante todo o ciclo de leitura, processamento e escrita. Sob disputa pelo mesmo cartão, vira fila e aumenta o risco de deadlock.
- **Lock otimista (`@Version`) com nova tentativa, e um `Cartao.debitar`.** Também funciona e deixaria o agregado como único dono da regra. Cada colisão de versão exigiria reler, reaplicar a regra e tentar de novo, e o update condicional resolve o mesmo problema numa só instrução.
- **Constraint `CHECK (saldo >= 0)`.** O MySQL 5.7 aceita a sintaxe mas não aplica a constraint (só a partir da 8.0.16).

## Consequências

- Funciona entre instâncias, sem laço de nova tentativa e com uma ida ao banco.
- A regra do saldo existe em dois lugares: a pré-checagem no agregado e o `UPDATE`. É um custo aceito.
- Foi validado contra o MySQL 5.7 real: um teste com 80 débitos simultâneos de R$ 10,00 num cartão de R$ 500,00 exige exatamente 50 aprovados e saldo zero. O teste foi confirmado como capaz de falhar: ele quebra se o `AND saldo >= :valor` for removido, se o débito virar "ler, subtrair, salvar" ou se o `save` virar merge.
- Cadastros simultâneos do mesmo número que acabou de ser apagado podem entrar em deadlock no InnoDB. A aplicação não apaga cartões, então isso só aparece em testes que limpam a tabela; o adapter repete a inserção, até 3 tentativas, e o resultado vira criado ou já existente.

**Quando reconsiderar.** Se surgirem regras de débito que dependam de mais de uma linha ou de estado além do saldo, como um limite diário ou um saldo por tipo de benefício, o update condicional deixa de bastar. Aí `@Version` com nova tentativa, ou um registro imutável de lançamentos (ledger) do qual o saldo seja derivado, passam a fazer sentido.
