package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.domain.cartao.SemBloqueioPorSenha;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.support.CartaoRepositoryEmMemoria;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import br.com.vr.miniautorizador.support.RelogioAjustavel;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercitado com fakes que têm comportamento real (repositório em memória e cifrador
 * determinístico): os testes verificam o resultado, não a sequência de chamadas.
 */
class CriarCartaoTest {

    private static final String NUMERO = "6549873025634501";
    private static final String SENHA = "1234";

    private final CartaoRepositoryEmMemoria repositorio = new CartaoRepositoryEmMemoria();
    private final CifradorSenhaFake cifrador = new CifradorSenhaFake();
    private final CriarCartao criarCartao = new CriarCartao(repositorio, cifrador);
    private final ConsultarSaldo consultarSaldo = new ConsultarSaldo(repositorio);
    private final AutorizarTransacao autorizarTransacao = new AutorizarTransacao(
            repositorio, new SemBloqueioPorSenha(), cifrador, new RelogioAjustavel());

    private final ListAppender<ILoggingEvent> registros = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CriarCartao.class);

    @BeforeEach
    void capturarLogs() {
        logger.setLevel(Level.INFO);
        registros.start();
        logger.addAppender(registros);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(registros);
        MDC.clear();
    }

    @Test
    @DisplayName("registra no log se o cartão foi criado ou era duplicado")
    void registraCriadoEDuplicado() {
        criarCartao.executar(NUMERO, SENHA);
        criarCartao.executar(NUMERO, SENHA);

        assertThat(registros.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("cartao.criado", "cartao.duplicado");
    }

    @Test
    @DisplayName("cria cartão com saldo inicial")
    void criaCartaoComSaldoInicial() {
        ResultadoCriacao resultado = criarCartao.executar(NUMERO, SENHA);

        assertThat(resultado).isEqualTo(ResultadoCriacao.CRIADO);
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("500.00"));
    }

    @Test
    @DisplayName("segunda criação do mesmo número não altera o cartão existente")
    void segundaCriacaoNaoAlteraCartao() {
        criarCartao.executar(NUMERO, SENHA);
        autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("100.00"));

        ResultadoCriacao resultado = criarCartao.executar(NUMERO, "9999");

        assertThat(resultado).isEqualTo(ResultadoCriacao.JA_EXISTENTE);
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("400.00"));
        assertThat(autorizarTransacao.executar(NUMERO, SENHA, new BigDecimal("1.00")))
                .as("a senha original continua valendo")
                .isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @Test
    @DisplayName("criação rejeita número em formato inválido")
    void criacaoRejeitaNumeroInvalido() {
        assertThatThrownBy(() -> criarCartao.executar("123", SENHA))
                .isInstanceOf(ValorInvalidoException.class);
    }
}
