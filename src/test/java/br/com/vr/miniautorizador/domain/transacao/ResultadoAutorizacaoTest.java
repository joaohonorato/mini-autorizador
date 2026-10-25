package br.com.vr.miniautorizador.domain.transacao;

import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao.Recusada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultadoAutorizacaoTest {

    @Test
    @DisplayName("exigir com regra atendida autoriza")
    void exigirComRegraAtendida() {
        assertThat(ResultadoAutorizacao.exigir(true, MotivoRecusa.SENHA_INVALIDA))
                .isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @ParameterizedTest(name = "exigir com regra violada recusa com {0}")
    @EnumSource(MotivoRecusa.class)
    void exigirComRegraViolada(MotivoRecusa motivo) {
        assertThat(ResultadoAutorizacao.exigir(false, motivo))
                .isEqualTo(ResultadoAutorizacao.recusada(motivo));
    }

    @Test
    @DisplayName("autorizada encadeia: o resultado é o da próxima etapa")
    void autorizadaAvaliaProximaEtapa() {
        ResultadoAutorizacao proxima = ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE);

        assertThat(ResultadoAutorizacao.autorizada().entao(() -> proxima)).isSameAs(proxima);
    }

    @Test
    @DisplayName("recusada encerra a cadeia sem avaliar a próxima etapa")
    void recusadaNaoAvaliaProximaEtapa() {
        ResultadoAutorizacao recusa = ResultadoAutorizacao.recusada(MotivoRecusa.SENHA_INVALIDA);
        AtomicBoolean proximaAvaliada = new AtomicBoolean();

        ResultadoAutorizacao resultado = recusa.entao(() -> {
            proximaAvaliada.set(true);
            return ResultadoAutorizacao.autorizada();
        });

        assertThat(resultado).isSameAs(recusa);
        assertThat(proximaAvaliada).isFalse();
    }

    @Test
    @DisplayName("na cadeia de regras, prevalece o motivo da primeira recusa")
    void primeiraRecusaPrevalece() {
        ResultadoAutorizacao resultado = ResultadoAutorizacao.exigir(true, MotivoRecusa.CARTAO_INEXISTENTE)
                .entao(() -> ResultadoAutorizacao.exigir(false, MotivoRecusa.SENHA_INVALIDA))
                .entao(() -> ResultadoAutorizacao.exigir(false, MotivoRecusa.SALDO_INSUFICIENTE));

        assertThat(resultado).isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SENHA_INVALIDA));
    }

    @Test
    @DisplayName("seRecusada executa a ação com o motivo e devolve a própria recusa")
    void seRecusadaExecutaAcao() {
        ResultadoAutorizacao recusa = ResultadoAutorizacao.recusada(MotivoRecusa.SENHA_INVALIDA);
        AtomicReference<MotivoRecusa> recebido = new AtomicReference<>();

        assertThat(recusa.seRecusada(recebido::set)).isSameAs(recusa);
        assertThat(recebido.get()).isEqualTo(MotivoRecusa.SENHA_INVALIDA);
    }

    @Test
    @DisplayName("seRecusada não executa a ação quando autorizada")
    void seRecusadaIgnoraAutorizada() {
        ResultadoAutorizacao autorizada = ResultadoAutorizacao.autorizada();
        AtomicBoolean executada = new AtomicBoolean();

        assertThat(autorizada.seRecusada(motivo -> executada.set(true))).isSameAs(autorizada);
        assertThat(executada).isFalse();
    }

    @Test
    @DisplayName("recusa sem motivo não é permitida")
    void recusaExigeMotivo() {
        assertThatThrownBy(() -> new Recusada(null)).isInstanceOf(NullPointerException.class);
    }
}
