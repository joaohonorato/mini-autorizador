package br.com.vr.miniautorizador.support;

import br.com.vr.miniautorizador.domain.cartao.BloqueioPorSenha;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.PoliticaDeBloqueio;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação em memória do bloqueio, com a mesma semântica do adapter JPA: a contagem de senhas
 * erradas é atômica, o erro que completa o limite bloqueia o cartão e o bloqueio vencido recomeça a contagem.
 */
public class BloqueioPorSenhaEmMemoria implements BloqueioPorSenha {

    private final Map<NumeroCartao, SenhasErradas> senhasErradas = new HashMap<>();

    @Override
    public synchronized ResultadoAutorizacao verificarDesbloqueado(NumeroCartao numero, Instant agora) {
        return ResultadoAutorizacao.exigir(
                vigentes(numero, agora).bloqueadoAte() == null, MotivoRecusa.CARTAO_BLOQUEADO);
    }

    @Override
    public synchronized void registrarSenhaErrada(NumeroCartao numero, Instant agora) {
        Optional.of(vigentes(numero, agora))
                .filter(erradas -> erradas.bloqueadoAte() == null)
                .ifPresent(erradas -> senhasErradas.put(numero, erradas.maisUma(agora)));
    }

    @Override
    public synchronized ResultadoAutorizacao registrarSenhaCerta(NumeroCartao numero, Instant agora) {
        return verificarDesbloqueado(numero, agora).entao(() -> {
            senhasErradas.remove(numero);
            return ResultadoAutorizacao.autorizada();
        });
    }

    private SenhasErradas vigentes(NumeroCartao numero, Instant agora) {
        return Optional.ofNullable(senhasErradas.get(numero))
                .filter(erradas -> !erradas.bloqueioVencidoEm(agora))
                .orElse(SenhasErradas.NENHUMA);
    }

    private record SenhasErradas(int quantidade, Instant bloqueadoAte) {

        static final SenhasErradas NENHUMA = new SenhasErradas(0, null);

        boolean bloqueioVencidoEm(Instant agora) {
            return bloqueadoAte != null && !bloqueadoAte.isAfter(agora);
        }

        SenhasErradas maisUma(Instant agora) {
            int novaQuantidade = quantidade + 1;
            Instant bloqueio = Optional.of(novaQuantidade)
                    .filter(q -> q >= PoliticaDeBloqueio.TENTATIVAS_DE_SENHA)
                    .map(q -> PoliticaDeBloqueio.bloqueioAte(agora))
                    .orElse(null);
            return new SenhasErradas(novaQuantidade, bloqueio);
        }
    }
}
