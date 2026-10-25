# 0003 - Recusa de transação como resultado (tipo selado), não como exceção

**Status:** aceita

## Contexto

Uma transação recusada (senha errada, saldo insuficiente, cartão inexistente) é um desfecho normal do negócio, não uma falha. Tratá-la como exceção esconde o contrato do caso de uso e espalha a tradução para HTTP entre o controller e um handler de exceções. O enunciado também propõe, como desafio opcional, construir a solução sem `if`.

## Decisão

A autorização devolve um `ResultadoAutorizacao`, uma interface selada com dois casos: `Autorizada` e `Recusada(motivo)`.

- `exigir(regraAtendida, motivo)` transforma uma regra num resultado.
- `entao(proximaEtapa)` encadeia: `Autorizada` executa a próxima etapa, `Recusada` devolve a própria recusa. A primeira recusa encerra a cadeia, e a ordem das regras é a ordem no código.
- `seRecusada(acao)` executa um efeito só numa recusa e devolve o próprio resultado.
- O controller traduz o resultado com um `switch` sobre o tipo selado, sem `default`.

O código de produção não tem `if`, operador ternário, `break` nem `continue`: as decisões saem do polimorfismo desse tipo, de `Optional` e do `switch`.

## Alternativas consideradas

- **Uma exceção (`TransacaoRecusadaException`) e um `@ExceptionHandler`.** Foi a primeira versão do projeto. Funciona, mas usa exceção para o fluxo normal e deixa o contrato do caso de uso implícito.
- **Um enum simples.** Perde o motivo da recusa como dado do resultado.

## Consequências

- O compilador obriga a tratar um desfecho novo no controller.
- A cadeia de `entao` com lambdas é menos óbvia para quem não conhece o padrão.
- Há um efeito colateral dentro da cadeia: `seRecusada` registra a senha errada. Reordenar as etapas mudaria o comportamento, por isso a ordem é protegida por testes (`AutorizarTransacaoTest` cobre cartão antes da senha, bloqueio antes da senha, senha antes do saldo e a contagem de erros).
