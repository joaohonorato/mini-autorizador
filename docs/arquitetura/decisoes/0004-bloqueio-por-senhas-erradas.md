# 0004 - Bloqueio por senhas erradas opcional e desligado por padrão

**Status:** aceita

## Contexto

A senha do cartão é um PIN de 4 dígitos. Sem limite de tentativas, quem conhece o número do cartão descobre a senha em até 10 mil chamadas. O BCrypt encarece cada tentativa, mas o espaço de busca é pequeno demais para protegê-lo sozinho.

Por outro lado, o enunciado lista só três motivos de recusa (`SALDO_INSUFICIENTE|SENHA_INVALIDA|CARTAO_INEXISTENTE`) e diz que os contratos precisam ser respeitados. Um bloqueio acrescenta um quarto motivo, `CARTAO_BLOQUEADO`, e quem testa várias senhas inválidas no mesmo cartão o encontraria.

## Decisão

O bloqueio existe, mas é opcional e **vem desligado**. `BLOQUEIO_SENHA_ATIVO=true` o liga.

- **Regra** (`PoliticaDeBloqueio`): 3 senhas erradas seguidas bloqueiam o cartão por 24 horas; a senha certa zera a contagem; durante o bloqueio a senha nem é conferida.
- **Porta e Null Object.** `BloqueioPorSenha` é a porta. Desligado, a implementação é `SemBloqueioPorSenha`, que sempre autoriza. Ligado, é `BloqueioPorSenhaJpaAdapter`. O caso de uso sempre chama a porta, sem testar uma flag.
- **Só os erros contam, e a contagem é atômica.** `registrarSenhaErrada` é um `UPDATE` condicional: o erro que completa o limite grava o fim do bloqueio na mesma instrução, e depois disso a contagem para. O estado fica nas colunas `tentativas_senha` e `bloqueado_ate`, fora do agregado.

## Alternativas consideradas

- **Reservar a tentativa antes de conferir a senha.** Limitaria uma rajada a exatamente 3 palpites. Foi a primeira versão, e ela contava também as transações legítimas: com 80 transações simultâneas da senha certa no mesmo cartão, só 23 foram autorizadas, em vez de 50. Como o desafio do enunciado pede justamente transações simultâneas no mesmo cartão, só os erros contam.
- **Sempre ligado.** Protege o PIN por padrão, mas expõe o roteiro do avaliador ao `CARTAO_BLOQUEADO` se ele repetir senhas inválidas.
- **Sem bloqueio.** Contrato intacto, mas o PIN fica exposto a palpite por força bruta.

## Consequências

- Por padrão a API segue o contrato à risca, e há um teste Cucumber, rodando na configuração padrão, que faz 5 senhas erradas seguidas e depois uma certa. Ligado, o bloqueio é testado contra o MySQL (`BloqueioPorSenhaJpaAdapterIT`, com 50 erros simultâneos contando exatamente 3) e pelo HTTP (`BloqueioPorSenhaApiIT`).
- Uma rajada de palpites simultâneos pode conferir mais de 3 senhas antes de o primeiro bloqueio valer, até o número de requisições que o servidor atende ao mesmo tempo.
- Com o bloqueio ligado, quem sabe o número de um cartão consegue bloqueá-lo por 24 horas. Em produção, o limite seria combinado com um limite por origem e com um fluxo de desbloqueio pelo emissor.
- Num ambiente real, a recomendação é ligar o bloqueio.
