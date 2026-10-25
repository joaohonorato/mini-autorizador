package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaInvalidaException;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaReutilizadaException;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia.Execucao;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.support.BloqueioPorSenhaEmMemoria;
import br.com.vr.miniautorizador.support.CartaoRepositoryEmMemoria;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import br.com.vr.miniautorizador.support.RegistroIdempotenciaEmMemoria;
import br.com.vr.miniautorizador.support.RelogioAjustavel;
import br.com.vr.miniautorizador.support.CartaoRepositoryQueObservaMdc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutorizarTransacaoIdempotenteTest {

    private static final String NUMERO = "6549873025634501";
    private static final String SENHA = "1234";
    private static final BigDecimal DEZ = new BigDecimal("10.00");

    private final CartaoRepositoryEmMemoria repositorio = new CartaoRepositoryEmMemoria();
    private final CifradorSenhaFake cifrador = new CifradorSenhaFake();
    private final RegistroIdempotenciaEmMemoria registro = new RegistroIdempotenciaEmMemoria();
    private final ConsultarSaldo consultarSaldo = new ConsultarSaldo(repositorio);
    private final AutorizarTransacaoIdempotente caso = new AutorizarTransacaoIdempotente(
            new AutorizarTransacao(repositorio, new BloqueioPorSenhaEmMemoria(), cifrador, new RelogioAjustavel()), registro);

    @BeforeEach
    void cadastrarCartao() {
        new CriarCartao(repositorio, cifrador).executar(NUMERO, SENHA);
    }

    @Test
    @DisplayName("o cartão mascarado fica no contexto de log durante a autorização e sai ao terminar")
    void contextoDeLogDuranteAAutorizacao() {
        CartaoRepositoryQueObservaMdc observador = new CartaoRepositoryQueObservaMdc();
        AutorizarTransacaoIdempotente observado = new AutorizarTransacaoIdempotente(
                new AutorizarTransacao(observador, new BloqueioPorSenhaEmMemoria(), cifrador, new RelogioAjustavel()),
                registro);

        observado.executar(Optional.empty(), NUMERO, SENHA, DEZ);

        assertThat(observador.cartoesVistos()).containsOnly("************4501");
        assertThat(MDC.get("numeroCartao")).isNull();
    }

    @Test
    @DisplayName("sem chave, cada envio autoriza e debita")
    void semChaveCadaEnvioDebita() {
        caso.executar(Optional.empty(), NUMERO, SENHA, DEZ);
        Execucao segundo = caso.executar(Optional.empty(), NUMERO, SENHA, DEZ);

        assertThat(segundo).isInstanceOf(Execucao.Nova.class);
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("480.00"));
        assertThat(registro.quantidade()).isZero();
    }

    @Test
    @DisplayName("com chave, o reenvio devolve o resultado original e debita uma vez só")
    void reenvioNaoDebitaDeNovo() {
        Execucao primeira = caso.executar(Optional.of("compra-1"), NUMERO, SENHA, DEZ);
        Execucao reenvio = caso.executar(Optional.of("compra-1"), NUMERO, SENHA, DEZ);

        assertThat(primeira).isEqualTo(new Execucao.Nova(ResultadoAutorizacao.autorizada()));
        assertThat(reenvio).isEqualTo(new Execucao.Repetida(ResultadoAutorizacao.autorizada()));
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("490.00"));
    }

    @Test
    @DisplayName("o reenvio de uma recusa devolve a mesma recusa, mesmo que o saldo mude depois")
    void reenvioDeRecusaDevolveARecusa() {
        BigDecimal maisQueOSaldo = new BigDecimal("500.01");
        Execucao primeira = caso.executar(Optional.of("compra-1"), NUMERO, SENHA, maisQueOSaldo);
        caso.executar(Optional.empty(), NUMERO, SENHA, new BigDecimal("1.00"));

        Execucao reenvio = caso.executar(Optional.of("compra-1"), NUMERO, SENHA, maisQueOSaldo);

        ResultadoAutorizacao recusa = ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE);
        assertThat(primeira).isEqualTo(new Execucao.Nova(recusa));
        assertThat(reenvio).isEqualTo(new Execucao.Repetida(recusa));
    }

    @Test
    @DisplayName("o valor com escala diferente é a mesma requisição")
    void valorComEscalaDiferenteEhOMesmoPedido() {
        caso.executar(Optional.of("compra-1"), NUMERO, SENHA, new BigDecimal("10"));

        assertThat(caso.executar(Optional.of("compra-1"), NUMERO, SENHA, new BigDecimal("10.00")))
                .isInstanceOf(Execucao.Repetida.class);
    }

    @Test
    @DisplayName("chaves diferentes são compras diferentes")
    void chavesDiferentesDebitamSeparadamente() {
        caso.executar(Optional.of("compra-1"), NUMERO, SENHA, DEZ);
        caso.executar(Optional.of("compra-2"), NUMERO, SENHA, DEZ);

        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("480.00"));
    }

    @Test
    @DisplayName("a mesma chave com outro valor ou outro cartão é recusada e não debita")
    void chaveReutilizadaEmOutraRequisicao() {
        caso.executar(Optional.of("compra-1"), NUMERO, SENHA, DEZ);

        assertThatThrownBy(() -> caso.executar(Optional.of("compra-1"), NUMERO, SENHA, new BigDecimal("20.00")))
                .isInstanceOf(ChaveIdempotenciaReutilizadaException.class);
        assertThatThrownBy(() -> caso.executar(Optional.of("compra-1"), "6000000000000002", SENHA, DEZ))
                .isInstanceOf(ChaveIdempotenciaReutilizadaException.class);
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("490.00"));
    }

    @Test
    @DisplayName("chave fora do formato é recusada antes de qualquer regra")
    void chaveInvalida() {
        assertThatThrownBy(() -> caso.executar(Optional.of("chave inválida"), NUMERO, SENHA, DEZ))
                .isInstanceOf(ChaveIdempotenciaInvalidaException.class);
        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("500.00"));
    }

    @Test
    @DisplayName("valor inválido continua sendo 400 e não reserva a chave")
    void valorInvalidoNaoReservaAChave() {
        assertThatThrownBy(() -> caso.executar(Optional.of("compra-1"), NUMERO, SENHA, new BigDecimal("-1")))
                .isInstanceOf(ValorInvalidoException.class);
        assertThat(registro.quantidade()).isZero();
    }
}
