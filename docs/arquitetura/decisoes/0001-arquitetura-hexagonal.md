# 0001 - Arquitetura hexagonal com DDD tático, na medida do problema

**Status:** aceita

## Contexto

O serviço tem três endpoints e uma tabela. O enunciado avalia o estilo e a qualidade do código e pede testes que "testem as classes de fato", não só passem por elas. Isso pesa a favor de regras que possam ser testadas sozinhas, sem framework e sem banco.

## Decisão

Quatro pacotes (`domain`, `application`, `infrastructure`, `api`), com a dependência apontando para o domínio. O domínio declara as portas de que precisa (`CartaoRepository`, `BloqueioPorSenha`, `CifradorSenha`), e a infraestrutura as implementa. Os casos de uso não têm anotações do Spring e são registrados como beans em `CasosDeUsoConfig`. `Cartao` é o agregado, e `NumeroCartao`, `Dinheiro` e `SenhaCifrada` são value objects que se validam na construção. O desenho completo está na [visão geral](../01-visao-geral.md).

## Alternativas consideradas

- **Camadas clássicas (controller, service, repository) com a entidade JPA como modelo.** Tem menos código. Mas as regras ficariam acopladas ao JPA, e os testes das regras dependeriam do Spring e do banco.
- **Lógica nos controllers.** Mais curto ainda, e impossível de testar sem HTTP.

## Consequências

- Os testes de domínio e de aplicação rodam sem contexto Spring, em milissegundos, com fakes que têm comportamento real (um repositório em memória e um cifrador determinístico), e afirmam o resultado em vez de verificar chamadas.
- Há mais arquivos do que o mínimo: portas, adapters e um mapper entre `CartaoEntity` e `Cartao`. Para um serviço deste tamanho isso é mais cerimônia do que o estritamente necessário; o custo foi aceito em troca de testabilidade e de um domínio livre de JPA.
- O agregado não é dono de toda a regra. O saldo e a contagem de senhas erradas precisam de atomicidade no banco, então parte da regra fica no SQL do adapter (veja o [0002](0002-concorrencia-update-condicional.md)). O agregado é rico só em parte.
- A regra de dependência é mantida por convenção, sem um teste de arquitetura que a proteja.
