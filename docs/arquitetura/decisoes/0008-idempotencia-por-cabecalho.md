# 0008 - Idempotência de `POST /transacoes` por cabeçalho

**Status:** aceita

## Contexto

Se a maquininha não recebe a resposta de uma compra (timeout, queda de rede) e reenvia a requisição, o cartão é debitado duas vezes. O contrato do enunciado não tem identificador de transação, e a resposta precisa continuar sendo o texto `OK` ou o motivo da recusa. A solução tem de preservar esse contrato e valer entre instâncias da aplicação.

## Decisão

- **Cabeçalho opcional `Idempotency-Key`**, o padrão de Stripe, Adyen e Mercado Pago. O cliente gera uma chave por tentativa de compra e a repete nos reenvios. Sem o cabeçalho, nada muda.
- **Reenvio devolve a resposta original**, com o mesmo status e o mesmo corpo (`201 OK` ou `422` com o motivo), sem executar a autorização de novo. A resposta repetida traz `Idempotent-Replayed: true`. Recusas também são gravadas, para que o reenvio sempre devolva o que a primeira tentativa devolveu e para que uma senha errada reenviada não conte outra tentativa do bloqueio.
- **A mesma chave em outra requisição** (outro cartão ou outro valor) devolve `422` em `ProblemDetail`, sem debitar. A comparação usa o SHA-256 do cartão e do valor. A senha fica de fora de propósito: um resumo dela no banco permitiria descobrir um PIN de 4 dígitos por tentativa.
- **Chave fora do formato** (1 a 64 caracteres entre letras, dígitos, `.`, `_`, `:` e `-`) devolve `400`.
- **Atômico no banco.** A tabela `transacao_idempotente` (migração V3) tem a chave como chave primária. A reserva da chave (`insert ignore`), a autorização, com o débito e a contagem de senhas erradas, e a gravação do resultado acontecem numa só transação. Se a autorização falhar com exceção, a transação desfaz tudo e a chave continua livre. Duas requisições simultâneas com a mesma chave, em instâncias diferentes, disputam a mesma linha: o InnoDB faz a segunda esperar o commit da primeira, e ela então lê o resultado gravado. Por isso não há um `409` para "ainda em processamento".
- **Validade de 24 horas** (`IDEMPOTENCIA_VALIDADE`), com uma limpeza periódica (`IDEMPOTENCIA_INTERVALO_LIMPEZA`, a cada hora). A chave pode valer até a limpeza seguinte além das 24 horas.
- **A chave é global**, não por cartão, porque a API tem uma única credencial.

## Alternativas consideradas

- **Campo novo no corpo.** Faria o mesmo trabalho, mas altera o contrato do enunciado e poderia quebrar quem o valida.
- **Janela de deduplicação (mesmo cartão, senha e valor em N segundos).** Foi descartada. É uma heurística que bloqueia compras legítimas repetidas, como duas passagens ou dois cafés, e recusar uma compra boa é pior do que o débito duplo. Em adquirência, a deduplicação usa o identificador da mensagem, não o valor.
- **Conferir a validade a cada requisição** com um `delete` ou `update` antes do `insert`. Foi tentado e produziu deadlocks: requisições simultâneas da mesma chave seguravam locks compartilhados e pediam o exclusivo ao mesmo tempo. A validade passou a ser responsabilidade da limpeza.
- **`select ... for update` ao ler o resultado gravado.** Pelo mesmo motivo, a leitura é simples. O instantâneo da transação só nasce nessa leitura, depois do commit da primeira requisição.
- **Bloqueio em memória por chave.** Não vale entre instâncias.

## Consequências

- Uma tabela nova, uma porta (`RegistroIdempotencia`), um caso de uso que envolve o `AutorizarTransacao` e uma tarefa agendada de limpeza.
- A proteção é opcional: só quem envia a chave fica protegido. Isso é o preço de não mudar o contrato.
- O corpo da resposta continua sendo texto puro. Uma versão em JSON seria uma decisão de contrato, não de idempotência.
- **Fica para depois, e depende de um registro de transações com identificador gerado pelo servidor:** o estorno (desfazimento), que um autorizador real oferece para o caso de a maquininha não saber se a compra passou, e a consulta do estado de uma transação. Hoje o saldo é um valor mutável, sem lançamentos.
