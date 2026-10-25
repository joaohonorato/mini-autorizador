package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;

import java.time.Instant;

/**
 * Porta do bloqueio do cartão por senhas erradas, regido pela {@link PoliticaDeBloqueio}.
 *
 * <p>O bloqueio é opcional: com ele desligado, a implementação é {@link SemBloqueioPorSenha}, e a
 * API nunca devolve CARTAO_BLOQUEADO, um motivo que não faz parte do contrato do enunciado.</p>
 */
public interface BloqueioPorSenha {

    /**
     * @return recusada com CARTAO_BLOQUEADO se o cartão está bloqueado em {@code agora}; autorizada
     *         caso contrário, inclusive quando o bloqueio já venceu
     */
    ResultadoAutorizacao verificarDesbloqueado(NumeroCartao numero, Instant agora);

    /**
     * Soma, de forma atômica, uma senha errada. O erro que completa o limite da
     * {@link PoliticaDeBloqueio} bloqueia o cartão até {@code PoliticaDeBloqueio.bloqueioAte(agora)}.
     * Se o bloqueio anterior já venceu, a contagem recomeça.
     */
    void registrarSenhaErrada(NumeroCartao numero, Instant agora);

    /**
     * Zera, de forma atômica, a contagem de senhas erradas.
     *
     * @return autorizada; ou recusada com CARTAO_BLOQUEADO se, nesse meio-tempo, senhas erradas
     *         simultâneas bloquearam o cartão
     */
    ResultadoAutorizacao registrarSenhaCerta(NumeroCartao numero, Instant agora);
}
