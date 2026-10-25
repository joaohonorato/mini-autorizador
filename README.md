# Mini autorizador

API REST que cadastra cartões de benefício, consulta saldo e autoriza transações, conforme o desafio técnico da VR Benefícios. O enunciado original está em [docs/ENUNCIADO.md](docs/ENUNCIADO.md).

**Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, Spring Security, Flyway e MySQL 5.7 (o banco do `docker-compose` fornecido). Nos testes: JUnit 5 com AssertJ e Mockito (unitários e contrato HTTP), Cucumber com Testcontainers (aceitação) e JaCoCo (cobertura).

## Como executar

Requer o **JDK 21**. O build recusa outras versões (`maven-enforcer-plugin`). Se o Java padrão da máquina for outro, aponte `JAVA_HOME` para um JDK 21 antes de rodar o Maven.

```bash
docker compose -f docker/docker-compose.yml up -d mysql
./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`, com autenticação Basic `username` / `password`. O esquema é criado na inicialização pelas migrações do Flyway (`src/main/resources/db/migration`), então nenhum passo manual é necessário. Um banco criado por uma versão anterior, que já tem a tabela `cartao` mas não tem o histórico do Flyway, é reconhecido como versão 1 e recebe só as migrações novas, sem perder os cartões.

Para rodar os testes:

```bash
./mvnw test      # JUnit 5: unitários e contrato HTTP, sem infraestrutura
./mvnw verify    # inclui os cenários Cucumber contra MySQL 5.7 via Testcontainers (requer Docker)
```

Depois do `verify`, ficam disponíveis dois relatórios. O da execução dos cenários está em `target/cucumber-report.html`. O de cobertura, que combina JUnit e Cucumber, está em `target/site/jacoco/index.html`.

**Teste manual com o Postman.** A pasta [docs/postman](docs/postman/README.md) tem uma collection com o roteiro do enunciado, os casos de borda e o bloqueio opcional, e um runner que cadastra 20 cartões de exemplo com saldos diferentes (`carga-inicial.csv`). Também roda pelo Newman, na linha de comando.

## API

Todas as rotas exigem Basic Auth (`username` / `password` por padrão). Sem credenciais, ou com credenciais erradas, a resposta é 401.

| Método e rota | Corpo da requisição | Sucesso | Recusa ou erro |
|---|---|---|---|
| `POST /cartoes` | `{"numeroCartao": "6549873025634501", "senha": "1234"}` | 201, devolve o corpo recebido, com `senha` antes de `numeroCartao`, como no enunciado | 422 com o mesmo corpo, se o cartão já existe. 400 se o corpo for inválido |
| `GET /cartoes/{numeroCartao}` | | 200, o saldo (`500.00` para um cartão novo) | 404, se o cartão não existe |
| `POST /transacoes` | `{"numeroCartao": "6549873025634501", "senhaCartao": "1234", "valor": 10.00}` | 201, corpo `OK` | 422 com o motivo no corpo: `SALDO_INSUFICIENTE`, `SENHA_INVALIDA` ou `CARTAO_INEXISTENTE`. 400 se o corpo for inválido. Aceita o cabeçalho opcional `Idempotency-Key` (veja [Idempotência](#idempotência)) |

```bash
curl -u username:password -H 'Content-Type: application/json' \
  -d '{"numeroCartao":"6549873025634501","senha":"1234"}' http://localhost:8080/cartoes

curl -u username:password -H 'Content-Type: application/json' \
  -d '{"numeroCartao":"6549873025634501","senhaCartao":"1234","valor":10.00}' http://localhost:8080/transacoes
```

Para reenviar uma compra sem risco de debitar duas vezes, acrescente `-H 'Idempotency-Key: compra-0001'` ao `POST /transacoes`.

As regras de validação das entradas estão em [Suposições](#suposições). O contrato original está em [docs/ENUNCIADO.md](docs/ENUNCIADO.md).

### Configuração

Todas as variáveis são opcionais. Os padrões são os valores do enunciado.

| Variável | Padrão | Efeito |
|---|---|---|
| `API_USER`, `API_PASSWORD` | `username`, `password` | Credencial Basic da API, comparada exatamente, com maiúsculas e minúsculas |
| `DB_HOST`, `DB_PORT` | `localhost`, `3306` | Endereço do MySQL |
| `DB_USER`, `DB_PASSWORD` | `root`, vazio | Credencial do MySQL |
| `BLOQUEIO_SENHA_ATIVO` | `false` | `true` liga o [bloqueio por senhas erradas](#bloqueio-por-senhas-erradas) |
| `IDEMPOTENCIA_VALIDADE` | `PT24H` | Por quanto tempo uma `Idempotency-Key` devolve a resposta original (duração ISO-8601) |
| `IDEMPOTENCIA_INTERVALO_LIMPEZA` | `PT1H` | De quanto em quanto tempo as chaves vencidas são apagadas |

Por exemplo, para subir com o bloqueio ligado: `BLOQUEIO_SENHA_ATIVO=true java -jar target/mini-autorizador-0.0.1-SNAPSHOT.jar`.

## Arquitetura

O projeto segue arquitetura hexagonal (ports and adapters) com os padrões táticos do DDD. O domínio não depende de Spring nem de JPA. O raciocínio de projeto, com diagramas, o fluxo de uma transação e os registros das decisões, está em [docs/arquitetura](docs/arquitetura/README.md).

```
domain/          regras de negócio puras
  cartao/        Cartao (agregado), NumeroCartao, SenhaCifrada, PoliticaDeBloqueio, portas CartaoRepository, BloqueioPorSenha e CifradorSenha
  transacao/     ResultadoAutorizacao (sealed: Autorizada | Recusada), MotivoRecusa, ChaveIdempotencia, porta RegistroIdempotencia
  compartilhado/ Dinheiro, ValorInvalidoException
application/     casos de uso: CriarCartao, ConsultarSaldo, AutorizarTransacao, AutorizarTransacaoIdempotente
infrastructure/  adapters: persistência JPA e migrações Flyway, BCrypt, configuração do Spring e da segurança
api/             tradução de exceções de entrada para HTTP (ApiExceptionHandler)
  controller/    CartaoController, TransacaoController
  dto/           NovoCartaoRequest, TransacaoRequest
```

**Agregado `Cartao`.** Concentra as regras de autorização que dependem do estado do cartão (`conferirSenha` e `verificarSaldo`) e só pode ser obtido por duas fábricas. `criar` aplica a regra do saldo inicial de R$ 500,00. `reconstituir` monta o cartão a partir do banco sem reaplicar regras de criação.

**Recusa é resultado, não exceção.** Uma transação recusada é um desfecho normal do negócio, então a autorização devolve um `ResultadoAutorizacao`, que é uma interface selada com dois casos: `Autorizada` e `Recusada(motivo)`. As regras se encadeiam com `entao`: a primeira recusa encerra a cadeia, o que garante a ordem cartão → não bloqueado → senha → saldo. `seRecusada` permite reagir a uma recusa sem interromper a cadeia, e é assim que uma senha errada é contada para o bloqueio. No controller, um `switch` sobre o tipo selado traduz cada caso para HTTP, e o compilador obriga a tratar os dois.

**Value objects.** `NumeroCartao`, `Dinheiro` e `SenhaCifrada` são imutáveis e se validam na construção, então um objeto inválido não chega a existir. `NumeroCartao` e `SenhaCifrada` mascaram o próprio `toString` para que o número completo do cartão e o hash da senha não vazem em logs. O log de erros SQL do Hibernate (`SqlExceptionHelper`) fica desligado em `application.yml`. Ele registraria a mensagem do MySQL no cadastro duplicado, que traz o número completo, antes de o adapter tratar o caso como cartão já existente. `CartaoRepositoryJpaAdapterIT` verifica que esse número não aparece no log.

**Domínio separado da persistência.** `CartaoEntity` é o modelo JPA, e `CartaoMapper` traduz entre ele e o agregado. A separação existe porque o JPA exige construtor vazio, campos não finais e proxies, e essas exigências enfraqueceriam o modelo de domínio. O mapper é escrito à mão porque o agregado não tem construtor público nem setters.

**Casos de uso sem anotações do Spring.** Eles são registrados como beans em `CasosDeUsoConfig`. Assim, as camadas de domínio e aplicação podem ser testadas sem subir contexto algum.

## Concorrência

**O desafio:** duas transações de R$ 10,00 chegam ao mesmo tempo, em instâncias diferentes, para um cartão com saldo de R$ 10,00.

Locks em memória não resolvem, porque cada instância tem a sua memória. A garantia precisa estar no banco. O débito é um update condicional atômico:

```sql
UPDATE cartao SET saldo = saldo - :valor WHERE numero = :numero AND saldo >= :valor
```

Se a instrução altera uma linha, o débito foi aplicado. Se altera zero, outra transação consumiu o saldo antes, e a resposta é `SALDO_INSUFICIENTE`. No cenário do desafio, uma transação é aprovada e a outra é recusada.

**Divisão de responsabilidades.** O agregado valida a senha e o saldo em memória, para falhar rápido e manter a regra expressa no domínio. O banco é a garantia final do invariante "saldo nunca negativo". O custo desse desenho é que a regra do saldo aparece em dois lugares: no agregado, como checagem sobre uma leitura que pode já estar desatualizada, e no SQL, que é a garantia de verdade. A alternativa seria um `Cartao.debitar` com `@Version` e nova tentativa, que deixaria o agregado como único dono da regra. Não foi usada porque, sob disputa pelo mesmo cartão, cada colisão de versão exige reler, reaplicar a regra e tentar de novo, e o update condicional resolve o mesmo problema numa só instrução, sem laço de nova tentativa. A regra segue testável no domínio, e o teste de integração contra o MySQL garante o invariante.

**Alternativas consideradas:**

- **Lock pessimista (`SELECT ... FOR UPDATE`).** Manteria a regra inteiramente no agregado, mas seguraria o lock da linha durante todo o ciclo de leitura, processamento e escrita. Sob disputa pelo mesmo cartão, isso vira fila e aumenta o risco de deadlock.
- **Lock otimista (`@Version`) com retry.** Também funciona, mas exige lógica de nova tentativa, e o update condicional resolve o problema em uma única instrução.
- **Constraint `CHECK (saldo >= 0)` como rede de segurança.** Foi descartada porque o MySQL 5.7 aceita a sintaxe mas não aplica a constraint (ela só é aplicada a partir do 8.0.16).

A criação de cartões segue o mesmo princípio. Em vez de consultar se o cartão existe e depois inserir, o que abriria uma janela de corrida, a aplicação tenta inserir e trata a violação da chave primária como "cartão já existente". Para isso, `CartaoEntity` implementa `Persistable`: sem isso, o `save` do Spring Data faria um merge e sobrescreveria o cartão existente. A aplicação não apaga cartões, mas cadastros simultâneos de um número recém-apagado, como nos testes que limpam a tabela, podem entrar em deadlock no InnoDB. Por precaução, o adapter repete a inserção desfeita por deadlock, até 3 tentativas. O mecanismo está no [ADR 0002](docs/arquitetura/decisoes/0002-concorrencia-update-condicional.md).

**Como isso é testado:** `CartaoRepositoryJpaAdapterIT` exercita o adapter diretamente contra o MySQL 5.7, sem a transação de teste, para que cada operação seja confirmada no banco. Ele cobre débito acima do saldo persistido, 80 débitos simultâneos e 20 cadastros simultâneos do mesmo número. O teste foi validado com três defeitos introduzidos de propósito, e falhou em todos: remover `and saldo >= :valor`, trocar o update condicional por "ler saldo, subtrair, salvar" e fazer o `save` virar merge. Além disso, o cenário Cucumber "Transações simultâneas não deixam o saldo negativo" dispara 80 transações de R$ 10,00 ao mesmo tempo contra um cartão de R$ 500,00. Ele verifica que exatamente 50 são aprovadas, 30 são recusadas com `SALDO_INSUFICIENTE` e o saldo termina em zero. A disputa entre instâncias é automatizada em `ConcorrenciaMultiInstanciaIT`, que sobe duas instâncias da aplicação no mesmo MySQL e falha se o `and saldo >= :valor` for removido do update.

## Idempotência

Se a maquininha não recebe a resposta de uma compra e reenvia a requisição, o cartão não pode ser debitado duas vezes. O cliente identifica cada tentativa de compra com o cabeçalho opcional `Idempotency-Key` (de 1 a 64 caracteres entre letras, dígitos, `.`, `_`, `:` e `-`; um UUID serve) e repete a mesma chave nos reenvios.

- **Reenvio com a mesma chave e a mesma requisição** devolve o status e o corpo da primeira resposta, `201 OK` ou o `422` com o motivo, sem debitar de novo e sem contar outra senha errada. A resposta repetida traz `Idempotent-Replayed: true`.
- **A mesma chave em outra requisição** (outro cartão ou outro valor) devolve `422` em `ProblemDetail`, sem debitar. A comparação usa o cartão e o valor, não a senha: um resumo da senha no banco permitiria descobrir um PIN de 4 dígitos.
- **Chave fora do formato** devolve `400`.
- **Sem o cabeçalho**, nada muda: cada envio é uma transação nova, como no enunciado.
- **Envios simultâneos com a mesma chave**, mesmo em instâncias diferentes, executam a autorização uma vez só. A chave é reservada, o débito é feito e o resultado é gravado na mesma transação do banco; o segundo envio espera a primeira transação terminar e então devolve o resultado dela.
- **Validade.** A chave vale por 24 horas (`IDEMPOTENCIA_VALIDADE`) e uma limpeza periódica apaga as vencidas, então ela pode valer um pouco além disso, até a limpeza seguinte.

O contrato do enunciado não muda, porque o cabeçalho é opcional. O desenho, as alternativas descartadas e o que ficou para depois estão no [ADR 0008](docs/arquitetura/decisoes/0008-idempotencia-por-cabecalho.md).

## Bloqueio por senhas erradas

A senha é um PIN de 4 dígitos, então sem limite de tentativas qualquer pessoa com o número do cartão descobriria a senha em até 10 mil chamadas. O BCrypt não resolve isso: ele encarece cada tentativa, mas o espaço de busca é pequeno demais. O que protege é o limite.

- **Desligado por padrão.** O enunciado lista só três motivos de recusa, e o bloqueio acrescenta um quarto. Para a API seguir o contrato à risca, ele só vale com `BLOQUEIO_SENHA_ATIVO=true`. Desligado, nenhum cartão fica bloqueado, por mais senhas erradas que receba (`SemBloqueioPorSenha`, coberto por um cenário Cucumber que roda com a configuração padrão). Ligado, vale a regra abaixo (`BloqueioPorSenhaJpaAdapter`, testado contra o MySQL e pelo HTTP em `BloqueioPorSenhaApiIT`). Num ambiente real, a recomendação é ligá-lo.
- **Regra** (`PoliticaDeBloqueio`): 3 senhas erradas seguidas bloqueiam o cartão por 24 horas, e a senha certa zera a contagem. Durante o bloqueio, a transação é recusada com `CARTAO_BLOQUEADO` mesmo com a senha certa, e a senha nem é conferida. O bloqueio acaba sozinho, sem endpoint de desbloqueio, e a contagem recomeça do zero.
- **Contrato.** `CARTAO_BLOQUEADO` é um motivo a mais, fora dos três do enunciado, e só aparece com o bloqueio ligado (`SALDO_INSUFICIENTE|SENHA_INVALIDA|CARTAO_INEXISTENTE`). Uma maquininha que siga o contrato à risca precisa tratar um motivo desconhecido como recusa.
- **Atômico no banco.** Cada senha errada é somada por um update condicional, como o débito: o erro que completa o limite grava o fim do bloqueio na mesma instrução, e depois disso o `where bloqueado_ate is null` não casa mais. Com 50 erros simultâneos, a contagem para em exatamente 3 (`CartaoRepositoryJpaAdapterIT`). O estado fica nas colunas `tentativas_senha` e `bloqueado_ate`, criadas pela migração V2, e não no agregado.
- **Alternativa descartada: reservar a tentativa antes de conferir a senha.** Limitaria uma rajada a 3 palpites, mas contaria também as transações legítimas, e o cenário de 80 transações simultâneas da senha certa deixaria de autorizar 50. Só as senhas erradas contam. O resultado medido está no [ADR 0004](docs/arquitetura/decisoes/0004-bloqueio-por-senhas-erradas.md).
- **Limite dessa escolha.** Uma rajada de palpites simultâneos pode conferir mais de 3 senhas antes de o primeiro bloqueio valer, até o número de requisições que o servidor atende ao mesmo tempo. Tentativas em sequência param no terceiro erro.

## Desafio sem `if`

O código de produção (`src/main`) não tem `if`, operador ternário, `break` nem `continue`. As decisões são resolvidas por:

- **`Optional` com `filter` e `orElseThrow`**, nas validações dos value objects;
- **polimorfismo no tipo selado `ResultadoAutorizacao`**, nas regras de autorização: `Autorizada.entao` avalia a próxima regra, `Recusada.entao` devolve a própria recusa e `seRecusada` só executa a contagem de senha errada numa recusa;
- **`switch` com pattern matching sobre o tipo selado**, na resposta de `POST /transacoes` (201 ou 422), sem `default`;
- **objeto nulo (`SemBloqueioPorSenha`)**, para o bloqueio desligado: em vez de testar uma flag, o caso de uso sempre chama o bloqueio, e a implementação desligada autoriza;
- **um mapa de enum para status HTTP**, na criação de cartão (`ResultadoCriacao` para 201 ou 422);
- **tradução de exceção por tipo**, no `@RestControllerAdvice`, para entradas inválidas (400).

## Suposições

- **Ordem das regras de autorização.** A ordem é cartão existente, cartão não bloqueado (etapa que só vale com o bloqueio ligado), senha correta e saldo suficiente. Sem cartão, não há senha para comparar. O bloqueio vem antes da senha para que um cartão bloqueado não sirva de oráculo de senhas. A senha vem antes do saldo para não revelar o saldo a quem não conhece a senha.
- **Bloqueio por senhas erradas** (3 tentativas, 24 horas, `CARTAO_BLOQUEADO`) é uma extensão opcional do contrato, desligada por padrão (`BLOQUEIO_SENHA_ATIVO=true` para ligar), descrita na seção [Bloqueio por senhas erradas](#bloqueio-por-senhas-erradas).
- **Valor igual ao saldo** é autorizado: o saldo fica zerado, nunca negativo.
- **Valor da transação** precisa ser maior que zero, ter no máximo duas casas decimais e até 17 dígitos inteiros (o que cabe na coluna `DECIMAL(19, 2)`). Valores com mais casas são rejeitados com 400 em vez de arredondados, para que nenhum centavo seja criado ou perdido silenciosamente. O limite de dígitos é verificado antes de qualquer cálculo, então um valor como `1e40000000` é rejeitado na hora em vez de ocupar a requisição por segundos.
- **Número do cartão** é tratado como texto, para preservar zeros à esquerda, e precisa ter exatamente 16 dígitos numéricos (o dígito verificador Luhn não é validado, pois o número de exemplo do enunciado não o satisfaz). Na criação, um formato inválido retorna 400. Na consulta e na transação, um número em formato inválido não pode corresponder a nenhum cartão cadastrado, então é tratado como inexistente (404 e `CARTAO_INEXISTENTE`). Isso inclui o número em branco na transação.
- **Senha em branco na transação** não é a senha do cartão, então é recusada com `SENHA_INVALIDA`, como qualquer senha errada.
- **Senha do cartão** é armazenada como hash BCrypt. A resposta de criação devolve a senha recebida porque o contrato exige isso, mas o banco nunca a guarda em texto puro. Como o BCrypt só considera os primeiros 72 bytes, o cadastro rejeita senhas maiores com 400. Sem esse limite, uma senha errada que só diferisse depois do 72º byte seria aceita.
- **Campos obrigatórios ausentes ou corpo malformado** retornam 400, no formato ProblemDetail (RFC 9457), com um `detail` em português que diz o que falhou: `Campos inválidos: valor deve ser maior que zero`, `Campo senha com tipo ou valor inválido` ou `Corpo da requisição ausente ou malformado`. Na transação, "ausente" quer dizer campo nulo ou não enviado; campo em branco segue para as regras de autorização. Os campos de texto (`numeroCartao`, `senha` e `senhaCartao`) precisam ser texto JSON: um número ou booleano no lugar recebe 400, porque a conversão para texto faria o número do cartão perder os zeros à esquerda sem aviso (`JacksonConfig`). Chave repetida no JSON (`"valor"` duas vezes, por exemplo) também recebe 400, em vez de ser lida com o último valor.
- **Cabeçalho `Accept`.** As rotas de cartão (`POST /cartoes` e `GET /cartoes/{numero}`) respondem só JSON: `application/json`, `*/*` ou nenhum `Accept` funcionam, e qualquer outro tipo (`text/plain`, `application/xml`) recebe 406 antes de o caso de uso rodar, para que um erro nunca deixe efeito colateral, como um cartão criado e uma resposta 406. `POST /transacoes` responde o texto `OK` do contrato, então também aceita `text/plain`, além de `application/json` e `*/*`. Um `Content-Type` diferente de `application/json` nas rotas com corpo recebe 415. Um método HTTP não suportado (`PUT /cartoes`, `GET /transacoes`) recebe 405.
- **Banco escolhido:** MySQL. O `docker-compose.yml` fornecido não foi alterado. As variáveis de ambiente estão em [Configuração](#configuração).

**Observação sobre o `docker-compose.yml` (só para quem usar o Mongo).** O compose monta `./scripts/init.js`, mas o arquivo fornecido se chama `init.users`, então o script de criação de usuário não roda. A conexão ainda funciona com o usuário root criado pelas variáveis `MONGO_INITDB_ROOT_*`, usando `authSource=admin`.

## Estratégia de testes

| Camada | Teste | Ferramenta | O que garante |
|---|---|---|---|
| Domínio | `CartaoTest`, `DinheiroTest`, `NumeroCartaoTest`, `SenhaCifradaTest`, `ResultadoAutorizacaoTest`, `ChaveIdempotenciaTest` | JUnit 5 + AssertJ | Regras e invariantes, sem framework |
| Aplicação | `CriarCartaoTest`, `ConsultarSaldoTest`, `AutorizarTransacaoTest`, `AutorizarTransacaoIdempotenteTest` | JUnit 5 + AssertJ | Fluxos completos com fakes de comportamento real, inclusive a corrida entre leitura e débito, o bloqueio por senhas erradas (com um relógio que o teste avança) e o contexto de log (MDC) preenchido só com o número mascarado durante a operação |
| Infraestrutura | `BCryptCifradorSenhaTest` | JUnit 5 + AssertJ | Hash com salt, verificação de senha e limite de 72 bytes |
| Infraestrutura | `CartaoRepositoryJpaAdapterTest` | JUnit 5 + Mockito | Nova tentativa do cadastro quando o banco desfaz a transação por deadlock |
| Infraestrutura | `CartaoRepositoryJpaAdapterIT` | `@DataJpaTest` + Testcontainers | Update condicional, chave duplicada e concorrência contra MySQL 5.7 real |
| Infraestrutura | `RegistroIdempotenciaJdbcAdapterIT` | `@JdbcTest` + Testcontainers | Reserva da chave, repetição do resultado, chave com outra requisição, ação que falha, 30 requisições simultâneas e limpeza, contra MySQL 5.7 real |
| Infraestrutura | `BloqueioPorSenhaJpaAdapterIT` | `@DataJpaTest` + Testcontainers | Contagem atômica de senhas erradas, vencimento do bloqueio e 50 erros simultâneos contra MySQL 5.7 real |
| API | `ContratoApiTest` | JUnit 5 + MockMvc + Mockito | Status, corpo, autenticação e cabeçalho `Accept` exatamente como no contrato |
| API | `BloqueioPorSenhaApiIT` | `@SpringBootTest` + Testcontainers | Bloqueio ligado pela configuração, de ponta a ponta pelo HTTP, inclusive a ordem: cartão bloqueado e sem saldo recusa por `CARTAO_BLOQUEADO` |
| API | `SenhaNoBancoIT` | `@SpringBootTest` + Testcontainers | Depois de um cadastro pelo HTTP, a coluna `senha` do MySQL guarda um hash BCrypt e não o texto original |
| API | `ConcorrenciaMultiInstanciaIT` | `SpringApplicationBuilder` + Testcontainers | Duas instâncias da aplicação (contextos Spring completos, com portas e pools próprios) no mesmo MySQL 5.7: 100 transações de R$ 10,00 divididas entre elas aprovam exatamente 50 e zeram o saldo |
| API | `IdempotenciaApiIT` | `@SpringBootTest` + Testcontainers | `Idempotency-Key` de ponta a ponta pelo HTTP: reenvio sem novo débito, recusa repetida, senha errada que não conta tentativa, chave reutilizada, chave inválida e 20 envios simultâneos |
| Aceitação | `features/autorizacao.feature` | Cucumber + Testcontainers | Roteiro da avaliação, recusas e concorrência contra MySQL 5.7 real |

**JUnit 5** é usado nos testes unitários e de contrato, com AssertJ nas asserções. Os testes parametrizados (`@ParameterizedTest` com `@CsvSource`) transformam cada regra de autorização em uma linha legível com entrada e motivo esperado, e `@DisplayName` descreve cada caso em português. Os casos de uso são testados com um repositório em memória e um cifrador determinístico, em vez de mocks que só verificam chamadas. Assim, os testes afirmam o resultado (o saldo depois da transação, o motivo da recusa) e não a implementação. Mocks do Mockito aparecem só onde a interação é o próprio objeto do teste, como "a senha é cifrada uma única vez" e "uma requisição inválida não chega ao caso de uso".

**Cucumber** descreve os testes de aceitação em português, no mesmo vocabulário do enunciado. Assim, o roteiro de avaliação vira uma especificação executável que qualquer pessoa do negócio consegue ler. Os valores são escritos com vírgula decimal (`500,00`), porque o Cucumber interpreta números conforme o idioma declarado na feature.

**Configuração da JVM:** o `pom.xml` carrega o Byte Buddy como agente da JVM nos testes. O Mockito o usa para criar mocks, e no Java 21 ele não consegue mais se anexar dinamicamente sem esse ajuste.

## Limitações conhecidas e o que mudaria em produção

- **Idempotência opcional.** Só quem envia `Idempotency-Key` fica protegido contra o débito duplicado; sem o cabeçalho, um reenvio debita de novo, porque o contrato do enunciado não traz identificador de transação. Também não há estorno nem consulta do estado de uma transação, que um autorizador real oferece para o caso de a maquininha não saber se a compra passou. Eles dependem de um registro de transações com identificador gerado pelo servidor (veja o [ADR 0008](docs/arquitetura/decisoes/0008-idempotencia-por-cabecalho.md)).
- **Dados de cartão (PCI DSS).** O número completo trafega na URL de consulta, que costuma aparecer em logs de acesso, e fica em texto puro no banco. Em produção, o número seria tokenizado e a consulta não o exporia na URL.
- **Registro das transações.** O enunciado dispensa persistir transações, então o saldo é um valor mutável. Um autorizador real manteria um registro imutável de lançamentos (ledger), do qual o saldo é derivado, para auditoria e conciliação.
- **Credencial única da API.** A autenticação Basic usa um único usuário fixo, como pede o enunciado. Em produção, seria autenticação por cliente (OAuth2 ou mTLS).
- **Bloqueio como arma contra o dono do cartão.** Com o bloqueio ligado, quem conhece o número de um cartão consegue bloqueá-lo por 24 horas com 3 senhas erradas. É o preço de proteger um PIN de 4 dígitos sem outro fator. Em produção, o limite seria combinado com um limite por origem (maquininha ou IP) e com um fluxo de desbloqueio pelo emissor.
- **Verificação da senha.** O BCrypt num PIN de 4 dígitos protege pouco por si só, e quem impede o palpite é o bloqueio, que vem desligado por padrão para não estender o contrato. Num autorizador real, o PIN costuma ser verificado em um HSM, e não por um hash guardado no banco da aplicação.
- **Observabilidade.** As recusas são registradas em log com o motivo. O próximo passo seria expor métricas de aprovação e recusa via Actuator e Micrometer.
