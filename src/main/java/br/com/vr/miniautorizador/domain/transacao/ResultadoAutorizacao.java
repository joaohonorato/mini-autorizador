package br.com.vr.miniautorizador.domain.transacao;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Desfecho de uma tentativa de autorização. Recusa é um resultado esperado do negócio,
 * não uma falha, por isso é um valor e não uma exceção.
 *
 * <p>Por ser selado, quem consome o resultado com {@code switch} é obrigado pelo compilador
 * a tratar os dois casos.</p>
 */
public sealed interface ResultadoAutorizacao {

    static ResultadoAutorizacao autorizada() {
        return new Autorizada();
    }

    static ResultadoAutorizacao recusada(MotivoRecusa motivo) {
        return new Recusada(motivo);
    }

    /**
     * Autorizada se a regra foi atendida; caso contrário, recusada com o motivo informado.
     */
    static ResultadoAutorizacao exigir(boolean regraAtendida, MotivoRecusa motivo) {
        return Optional.of(regraAtendida)
                .filter(Boolean::booleanValue)
                .map(atendida -> autorizada())
                .orElseGet(() -> recusada(motivo));
    }

    /**
     * Encadeia a próxima regra. Só é avaliada se esta etapa foi autorizada: a primeira
     * recusa encerra a cadeia, o que preserva a ordem entre as regras.
     */
    ResultadoAutorizacao entao(Supplier<ResultadoAutorizacao> proximaEtapa);

    /**
     * Executa a ação só se esta etapa foi recusada, com o motivo da recusa, e devolve o próprio
     * resultado. É o complemento de {@link #entao}: permite reagir a uma recusa sem interromper a cadeia.
     */
    ResultadoAutorizacao seRecusada(Consumer<MotivoRecusa> acao);

    record Autorizada() implements ResultadoAutorizacao {

        @Override
        public ResultadoAutorizacao entao(Supplier<ResultadoAutorizacao> proximaEtapa) {
            return proximaEtapa.get();
        }

        @Override
        public ResultadoAutorizacao seRecusada(Consumer<MotivoRecusa> acao) {
            return this;
        }
    }

    record Recusada(MotivoRecusa motivo) implements ResultadoAutorizacao {

        public Recusada {
            Objects.requireNonNull(motivo, "motivo");
        }

        @Override
        public ResultadoAutorizacao entao(Supplier<ResultadoAutorizacao> proximaEtapa) {
            return this;
        }

        @Override
        public ResultadoAutorizacao seRecusada(Consumer<MotivoRecusa> acao) {
            acao.accept(motivo);
            return this;
        }
    }
}
