# language: pt
Funcionalidade: Autorização de transações de cartões de benefício
  Como uma maquininha de cartão
  Quero cadastrar cartões, consultar saldos e autorizar transações
  Para que apenas compras válidas debitem o saldo do benefício

  Cenário: Roteiro da avaliação, do cadastro até o saldo acabar
    Quando eu cadastro o cartão "6549873025634501" com a senha "1234"
    Então a resposta tem status 201
    E o corpo da resposta traz o cartão "6549873025634501" com a senha "1234"
    E o saldo do cartão "6549873025634501" é 500,00
    Quando eu faço transações de 60,00 no cartão "6549873025634501" com a senha "1234" até uma ser recusada, conferindo o saldo após cada uma
    Então 8 transações foram autorizadas
    E a resposta tem status 422
    E o corpo da resposta é "SALDO_INSUFICIENTE"
    E o saldo do cartão "6549873025634501" é 20,00

  Cenário: Cadastro de cartão já existente
    Dado que o cartão "5000000000000001" está cadastrado com a senha "1234"
    Quando eu cadastro o cartão "5000000000000001" com a senha "9999"
    Então a resposta tem status 422
    E o corpo da resposta traz o cartão "5000000000000001" com a senha "9999"
    E o saldo do cartão "5000000000000001" é 500,00

  Esquema do Cenário: Transação recusada por <motivo>
    Dado que o cartão "<cadastrado>" está cadastrado com a senha "1234"
    Quando eu faço uma transação de <valor> no cartão "<cartao>" com a senha "<senha>"
    Então a resposta tem status 422
    E o corpo da resposta é "<motivo>"
    E o saldo do cartão "<cadastrado>" é 500,00

    Exemplos:
      | cadastrado       | cartao           | senha | valor  | motivo             |
      | 5000000000000002 | 5000000000000002 | 0000  | 10,00  | SENHA_INVALIDA     |
      | 5000000000000003 | 5000000000000003 | 1234  | 500,01 | SALDO_INSUFICIENTE |
      | 5000000000000004 | 1111222233334444 | 1234  | 10,00  | CARTAO_INEXISTENTE |
      | 5000000000000005 | 5000000000000005 | 0000  | 999,00 | SENHA_INVALIDA     |
      | 5000000000000006 | 5000000000000006 |       | 10,00  | SENHA_INVALIDA     |
      | 5000000000000007 |                  | 1234  | 10,00  | CARTAO_INEXISTENTE |

  Esquema do Cenário: Cadastro com número fora do formato é rejeitado com 400
    Quando eu cadastro o cartão "<numero>" com a senha "1234"
    Então a resposta tem status 400
    E o detalhe do erro é "Número do cartão deve ter 16 dígitos"

    Exemplos:
      | numero              |
      | 654987302563450     |
      | 65498730256345012   |
      | 654987302563450A    |
      | 6549 8730 2563 4501 |
      | 6549-8730-2563-4501 |

  Esquema do Cenário: Número fora do formato é tratado como cartão inexistente
    Quando eu consulto o saldo do cartão "<numero>"
    Então a resposta tem status 404
    E a resposta não tem corpo
    Quando eu faço uma transação de 10,00 no cartão "<numero>" com a senha "1234"
    Então a resposta tem status 422
    E o corpo da resposta é "CARTAO_INEXISTENTE"

    Exemplos:
      | numero              |
      | 654987302563450     |
      | 65498730256345012   |
      | 654987302563450A    |
      | 6549 8730 2563 4501 |
      | 6549-8730-2563-4501 |

  Esquema do Cenário: Valor inválido é rejeitado com 400 e não altera o saldo
    Dado que o cartão "<cartao>" está cadastrado com a senha "1234"
    Quando eu faço uma transação de <valor> no cartão "<cartao>" com a senha "1234"
    Então a resposta tem status 400
    E o detalhe do erro é "<detalhe>"
    E o saldo do cartão "<cartao>" é 500,00

    Exemplos:
      | cartao           | valor  | detalhe                                                                                             |
      | 5000000000000023 | 0,00   | Campos inválidos: valor deve ser maior que zero                                                     |
      | 5000000000000024 | -1,00  | Campos inválidos: valor deve ser maior que zero                                                     |
      | 5000000000000025 | 10,005 | Valor monetário deve ser não negativo, ter no máximo duas casas decimais e até 17 dígitos inteiros |

  Cenário: Senhas erradas repetidas não bloqueiam o cartão, que é o comportamento padrão
    Dado que o cartão "5000000000000008" está cadastrado com a senha "1234"
    Quando eu faço uma transação de 10,00 no cartão "5000000000000008" com a senha "0000"
    E eu faço uma transação de 10,00 no cartão "5000000000000008" com a senha "0001"
    E eu faço uma transação de 10,00 no cartão "5000000000000008" com a senha "0002"
    E eu faço uma transação de 10,00 no cartão "5000000000000008" com a senha "0003"
    E eu faço uma transação de 10,00 no cartão "5000000000000008" com a senha "0004"
    Então a resposta tem status 422
    E o corpo da resposta é "SENHA_INVALIDA"
    Quando eu faço uma transação de 10,00 no cartão "5000000000000008" com a senha "1234"
    Então a resposta tem status 201
    E o corpo da resposta é "OK"
    E o saldo do cartão "5000000000000008" é 490,00

  Cenário: Reenviar a mesma compra com a mesma chave de idempotência não debita de novo
    Dado que o cartão "5000000000000021" está cadastrado com a senha "1234"
    Quando eu faço uma transação de 10,00 no cartão "5000000000000021" com a senha "1234" usando a chave "compra-0021"
    Então a resposta tem status 201
    E o corpo da resposta é "OK"
    Quando eu faço uma transação de 10,00 no cartão "5000000000000021" com a senha "1234" usando a chave "compra-0021"
    Então a resposta tem status 201
    E o corpo da resposta é "OK"
    E a resposta é uma repetição da original
    E o saldo do cartão "5000000000000021" é 490,00

  Cenário: Reutilizar a chave de idempotência em outra compra é recusado
    Dado que o cartão "5000000000000022" está cadastrado com a senha "1234"
    Quando eu faço uma transação de 10,00 no cartão "5000000000000022" com a senha "1234" usando a chave "compra-0022"
    E eu faço uma transação de 20,00 no cartão "5000000000000022" com a senha "1234" usando a chave "compra-0022"
    Então a resposta tem status 422
    E o saldo do cartão "5000000000000022" é 490,00

  Cenário: Consulta de saldo de cartão inexistente
    Quando eu consulto o saldo do cartão "1111222233334444"
    Então a resposta tem status 404
    E a resposta não tem corpo

  Cenário: Requisição sem autenticação
    Quando eu consulto o saldo do cartão "6549873025634501" sem credenciais
    Então a resposta tem status 401

  Cenário: Página de erro não é pública
    Quando eu acesso "/error" sem credenciais
    Então a resposta tem status 401

  Cenário: Transações simultâneas não deixam o saldo negativo
    Dado que o cartão "4000000000000002" está cadastrado com a senha "1234"
    Quando 80 transações de 10,00 são disparadas ao mesmo tempo no cartão "4000000000000002" com a senha "1234"
    Então 50 transações foram autorizadas
    E 30 transações foram recusadas com "SALDO_INSUFICIENTE"
    E o saldo do cartão "4000000000000002" é 0,00
