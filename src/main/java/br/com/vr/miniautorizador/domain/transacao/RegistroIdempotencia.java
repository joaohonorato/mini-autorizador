package br.com.vr.miniautorizador.domain.transacao;

import java.util.function.Supplier;

/**
 * Porta que garante que a autorização de uma chave de idempotência acontece uma única vez.
 *
 * <p>A implementação executa a ação e grava o resultado junto, de forma atômica, e isso vale entre
 * instâncias da aplicação: de duas requisições simultâneas com a mesma chave, só uma executa a ação;
 * a outra espera e recebe o resultado gravado. Se a ação falhar com exceção, nada é gravado e a chave
 * continua livre.</p>
 */
public interface RegistroIdempotencia {

    /**
     * @param impressao resumo da requisição, para detectar a chave reutilizada em outra requisição
     * @throws ChaveIdempotenciaReutilizadaException se a chave já existe com outra impressão
     */
    Execucao executarUmaVez(ChaveIdempotencia chave, String impressao, Supplier<ResultadoAutorizacao> acao);

    /**
     * Como a requisição foi atendida: executada agora ou repetida a partir do resultado já gravado.
     */
    sealed interface Execucao {

        ResultadoAutorizacao resultado();

        record Nova(ResultadoAutorizacao resultado) implements Execucao {
        }

        record Repetida(ResultadoAutorizacao resultado) implements Execucao {
        }
    }
}
