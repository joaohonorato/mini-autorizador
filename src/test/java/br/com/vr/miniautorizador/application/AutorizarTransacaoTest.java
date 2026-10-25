package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.SemBloqueioPorSenha;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.support.BloqueioPorSenhaEmMemoria;
import br.com.vr.miniautorizador.support.CartaoRepositoryEmMemoria;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import br.com.vr.miniautorizador.support.RelogioAjustavel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercitado com fakes que têm comportamento real (repositório em memória e cifrador
 * determinístico): os testes verificam o resultado, não a sequência de chamadas.
 */
class AutorizarTransacaoTest {

    private static final String NUMERO = "6549873025634501";
    private static final String SENHA = "1234";

    private final CartaoRepositoryEmMemoria repositorio = new CartaoRepositoryEmMemoria();
    private final CifradorSenhaFake cifrador = new CifradorSenhaFake();
    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final ConsultarSaldo consultarSaldo = new ConsultarSaldo(repositorio);
    private final BloqueioPorSenhaEmMemoria bloqueio = new BloqueioPorSenhaEmMemoria();
    private final AutorizarTransacao autorizarTransacao = new AutorizarTransacao(repositorio, bloqueio, cifrador, relogio);

    @BeforeEach
    void cadastrarCartao() {
        new CriarCartao(repositorio, cifrador).executar(NUMERO, SENHA);
    }

    @Test
    @DisplayName("transação autorizada debita o saldo")
    void transacaoAutorizadaDebitaSaldo() {
        ResultadoAutorizacao resultado = autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("10.00"));

        assertThat(resultado).isEqualTo(ResultadoAutorizacao.autorizada());
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("490.00"));
    }

    @Test
    @DisplayName("transação de valor igual ao saldo autoriza e zera o saldo")
    void transacaoIgualAoSaldoAutoriza() {
        ResultadoAutorizacao resultado = autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("500.00"));

        assertThat(resultado).isEqualTo(ResultadoAutorizacao.autorizada());
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("0.00"));
    }

    @Test
    @DisplayName("transações sucessivas consomem o saldo até ser insuficiente")
    void transacoesSucessivasConsomemSaldo() {
        List<ResultadoAutorizacao> resultados = IntStream.range(0, 50)
                .mapToObj(i -> autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .toList();

        assertThat(resultados).containsOnly(ResultadoAutorizacao.autorizada());
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("0.00"));
        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("0.01")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));
    }

    @ParameterizedTest(name = "recusa {3} sem debitar: cartão \"{0}\", senha \"{1}\", valor {2}")
    @CsvSource(textBlock = """
            1111222233334444, 1234, 10.00,  CARTAO_INEXISTENTE
            abc,              1234, 10.00,  CARTAO_INEXISTENTE
            # número fora do formato (não são 16 dígitos numéricos) não corresponde a nenhum cartão
            654987302563450,  1234, 10.00,  CARTAO_INEXISTENTE
            65498730256345012, 1234, 10.00, CARTAO_INEXISTENTE
            654987302563450A, 1234, 10.00,  CARTAO_INEXISTENTE
            '6549 8730 2563 4501', 1234, 10.00, CARTAO_INEXISTENTE
            6549-8730-2563-4501, 1234, 10.00, CARTAO_INEXISTENTE
            # inexistência vem antes da senha
            1111222233334444, 0000, 999.00, CARTAO_INEXISTENTE
            6549873025634501, 0000, 10.00,  SENHA_INVALIDA
            # senha vem antes do saldo
            6549873025634501, 0000, 999.00, SENHA_INVALIDA
            6549873025634501, 1234, 500.01, SALDO_INSUFICIENTE
            # campos em branco não correspondem a cartão nem a senha cadastrados
            '',               1234, 10.00,  CARTAO_INEXISTENTE
            6549873025634501, '',   10.00,  SENHA_INVALIDA
            6549873025634501, '  ', 10.00,  SENHA_INVALIDA
            """)
    void recusaSemDebitar(String numero, String senha, BigDecimal valor, MotivoRecusa motivo) {
        ResultadoAutorizacao resultado = autorizarTransacao.executar(numero, senha, valor);

        assertThat(resultado).isEqualTo(ResultadoAutorizacao.recusada(motivo));
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("500.00"));
    }

    @Test
    @DisplayName("recusa quando outra instância consumiu o saldo entre a leitura e o débito")
    void recusaQuandoSaldoFoiConsumidoEntreLeituraEDebito() {
        // repositório em que o saldo persistido é zerado logo antes do débito
        CartaoRepositoryEmMemoria repositorioComCorrida = new CartaoRepositoryEmMemoria() {
            @Override
            public synchronized ResultadoAutorizacao debitar(NumeroCartao numero, Dinheiro valor) {
                Cartao atual = buscar(numero).orElseThrow();
                substituir(Cartao.reconstituir(numero, atual.senha(), Dinheiro.de("0.00")));
                return super.debitar(numero, valor);
            }
        };
        new CriarCartao(repositorioComCorrida, cifrador).executar(NUMERO, SENHA);
        AutorizarTransacao autorizar = new AutorizarTransacao(repositorioComCorrida, bloqueio, cifrador, relogio);

        assertThat(autorizar.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .as("o agregado vê saldo suficiente, mas o débito condicional não")
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));
    }

    @Test
    @DisplayName("3 senhas erradas seguidas bloqueiam o cartão, mesmo para a senha certa e sem debitar")
    void tresSenhasErradasBloqueiam() {
        List<ResultadoAutorizacao> erradas = IntStream.range(0, 3)
                .mapToObj(i -> autorizarTransacao.executar(NUMERO, "0000", new BigDecimal("10.00")))
                .toList();

        assertThat(erradas).containsOnly(ResultadoAutorizacao.recusada(MotivoRecusa.SENHA_INVALIDA));
        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("500.00"));
    }

    @Test
    @DisplayName("cartão bloqueado responde CARTAO_BLOQUEADO também para senha errada, sem conferir a senha")
    void cartaoBloqueadoNaoConfereSenha() {
        IntStream.range(0, 3).forEach(i -> autorizarTransacao.executar(NUMERO, "0000", new BigDecimal("10.00")));

        assertThat(autorizarTransacao.executar(NUMERO, "9999", new BigDecimal("10.00")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));
    }

    @Test
    @DisplayName("com o bloqueio desligado, que é o padrão, senhas erradas nunca bloqueiam o cartão")
    void semBloqueioSenhasErradasNaoBloqueiam() {
        AutorizarTransacao semBloqueio =
                new AutorizarTransacao(repositorio, new SemBloqueioPorSenha(), cifrador, relogio);

        List<ResultadoAutorizacao> erradas = IntStream.range(0, 10)
                .mapToObj(i -> semBloqueio.executar(NUMERO, "0000", new BigDecimal("10.00")))
                .toList();

        assertThat(erradas).containsOnly(ResultadoAutorizacao.recusada(MotivoRecusa.SENHA_INVALIDA));
        assertThat(semBloqueio.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @Test
    @DisplayName("a senha certa zera as tentativas, mesmo quando o saldo não cobre o valor")
    void senhaCertaZeraTentativas() {
        autorizarTransacao.executar(NUMERO, "0000", new BigDecimal("10.00"));
        autorizarTransacao.executar(NUMERO, "0000", new BigDecimal("10.00"));
        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("999.00")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));

        autorizarTransacao.executar(NUMERO, "0000", new BigDecimal("10.00"));
        autorizarTransacao.executar(NUMERO, "0000", new BigDecimal("10.00"));

        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .as("só 2 erradas desde a última senha certa: o cartão não está bloqueado")
                .isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @Test
    @DisplayName("o bloqueio dura 24 horas e depois as tentativas recomeçam")
    void bloqueioVenceEm24Horas() {
        IntStream.range(0, 3).forEach(i -> autorizarTransacao.executar(NUMERO, "0000", new BigDecimal("10.00")));

        relogio.avancar(Duration.ofHours(24).minusSeconds(1));
        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .isEqualTo(ResultadoAutorizacao.autorizada());
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("490.00"));
    }

    @Test
    @DisplayName("tentativas em cartão inexistente não bloqueiam o cartão cadastrado")
    void cartaoInexistenteNaoConsomeTentativas() {
        IntStream.range(0, 5).forEach(i -> autorizarTransacao.executar("1111222233334444", "0000", new BigDecimal("10.00")));

        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("10.00")))
                .isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @ParameterizedTest(name = "rejeita valor de transação {0}")
    @ValueSource(strings = {"0", "-5.00", "1.001", "1e40000000"})
    void rejeitaValorDeTransacaoInvalido(BigDecimal valor) {
        assertThatThrownBy(() -> autorizarTransacao.executar(NUMERO, SENHA, valor))
                .isInstanceOf(ValorInvalidoException.class);
    }
}
