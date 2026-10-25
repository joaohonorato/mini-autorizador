# Arquitetura do mini-autorizador

Estes documentos explicam como o código está organizado e por que ele é assim. O [README da raiz](../../README.md) tem como executar, as suposições de negócio e a estratégia de testes; aqui está o raciocínio de projeto.

| Documento | O que responde |
|---|---|
| [01 - Visão geral](01-visao-geral.md) | Quais são as camadas, quem pode depender de quem e onde fica cada coisa. |
| [02 - Fluxo da transação](02-fluxo-da-transacao.md) | O que acontece, passo a passo, entre `POST /transacoes` e a resposta, e onde a concorrência é resolvida. |

## Decisões

Cada decisão importante tem um registro curto, com o contexto, as alternativas que foram descartadas e o que cada escolha custa.

| Registro | Decisão |
|---|---|
| [0001](decisoes/0001-arquitetura-hexagonal.md) | Arquitetura hexagonal com DDD tático, na medida do problema |
| [0002](decisoes/0002-concorrencia-update-condicional.md) | Concorrência resolvida no banco, com update condicional |
| [0003](decisoes/0003-recusa-como-resultado.md) | Recusa de transação como resultado (tipo selado), não como exceção |
| [0004](decisoes/0004-bloqueio-por-senhas-erradas.md) | Bloqueio por senhas erradas opcional e desligado por padrão |
| [0005](decisoes/0005-esquema-com-flyway.md) | Esquema do banco versionado com Flyway |
| [0006](decisoes/0006-rigor-nas-bordas-da-api.md) | Rigor nas bordas da API |
| [0007](decisoes/0007-documentacao-openapi.md) | Documentação OpenAPI e Swagger UI atrás do Basic Auth (revogada) |
| [0008](decisoes/0008-idempotencia-por-cabecalho.md) | Idempotência de `POST /transacoes` por cabeçalho |

Os diagramas ficam em [img](img/) como arquivos `.drawio.svg`: o GitHub e qualquer visualizador de Markdown os mostram como imagem, e a extensão Draw.io Integration (VS Code) ou o diagrams.net os abrem para edição. Se mudar o código, mude o documento e o diagrama juntos.
