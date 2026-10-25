package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;

import java.time.Instant;

/**
 * Bloqueio desligado: nenhum cartão fica bloqueado, por mais senhas erradas que receba. É o
 * comportamento padrão da aplicação, que segue o contrato do enunciado à risca.
 */
public final class SemBloqueioPorSenha implements BloqueioPorSenha {

    @Override
    public ResultadoAutorizacao verificarDesbloqueado(NumeroCartao numero, Instant agora) {
        return ResultadoAutorizacao.autorizada();
    }

    @Override
    public void registrarSenhaErrada(NumeroCartao numero, Instant agora) {
        // sem bloqueio, não há contagem a manter
    }

    @Override
    public ResultadoAutorizacao registrarSenhaCerta(NumeroCartao numero, Instant agora) {
        return ResultadoAutorizacao.autorizada();
    }
}
