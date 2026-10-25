package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.cartao.BloqueioPorSenha;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.PoliticaDeBloqueio;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Adapter do bloqueio por senhas erradas com updates atômicos no MySQL. Só existe quando o
 * bloqueio está ligado ({@code autorizador.bloqueio-senha.ativo=true}); desligado, o
 * {@code CasosDeUsoConfig} registra o {@code SemBloqueioPorSenha}.
 */
@Component
@ConditionalOnProperty(name = "autorizador.bloqueio-senha.ativo", havingValue = "true")
class BloqueioPorSenhaJpaAdapter implements BloqueioPorSenha {

    private static final int UMA_LINHA_ALTERADA = 1;

    private final CartaoJpaRepository jpa;

    BloqueioPorSenhaJpaAdapter(CartaoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ResultadoAutorizacao verificarDesbloqueado(NumeroCartao numero, Instant agora) {
        return ResultadoAutorizacao.exigir(
                jpa.contarDesbloqueado(numero.valor(), agora) > 0, MotivoRecusa.CARTAO_BLOQUEADO);
    }

    @Override
    public void registrarSenhaErrada(NumeroCartao numero, Instant agora) {
        jpa.encerrarBloqueioVencido(numero.valor(), agora);
        jpa.registrarSenhaErrada(
                numero.valor(), PoliticaDeBloqueio.TENTATIVAS_DE_SENHA, PoliticaDeBloqueio.bloqueioAte(agora));
    }

    @Override
    public ResultadoAutorizacao registrarSenhaCerta(NumeroCartao numero, Instant agora) {
        int linhasAlteradas = jpa.registrarSenhaCerta(numero.valor(), agora);
        return ResultadoAutorizacao.exigir(linhasAlteradas == UMA_LINHA_ALTERADA, MotivoRecusa.CARTAO_BLOQUEADO);
    }
}
