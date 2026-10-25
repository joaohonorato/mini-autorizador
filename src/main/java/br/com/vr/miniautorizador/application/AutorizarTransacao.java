package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.cartao.BloqueioPorSenha;
import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.CartaoRepository;
import br.com.vr.miniautorizador.domain.cartao.CifradorSenha;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Caso de uso: autorizar uma transação e debitar o valor do saldo do cartão.
 *
 * <p>Ordem das regras: cartão existente, cartão não bloqueado, senha correta, saldo suficiente.
 * A senha e o saldo são conferidos pelo agregado; a contagem de senhas erradas e o débito são
 * aplicados de forma atômica pelos adapters. A senha certa zera a contagem, mesmo que o saldo
 * não cubra o valor. Com o bloqueio desligado, as duas etapas do bloqueio não fazem nada.</p>
 */
public class AutorizarTransacao {

    private static final Logger LOG = LoggerFactory.getLogger(AutorizarTransacao.class);

    private final CartaoRepository repositorio;
    private final BloqueioPorSenha bloqueio;
    private final CifradorSenha cifrador;
    private final Clock relogio;

    public AutorizarTransacao(CartaoRepository repositorio, BloqueioPorSenha bloqueio,
                              CifradorSenha cifrador, Clock relogio) {
        this.repositorio = repositorio;
        this.bloqueio = bloqueio;
        this.cifrador = cifrador;
        this.relogio = relogio;
    }

    public ResultadoAutorizacao executar(String numeroCartao, String senhaCartao, BigDecimal valor) {
        Dinheiro valorTransacao = Dinheiro.positivo(valor);
        ResultadoAutorizacao resultado = NumeroCartao.tentar(numeroCartao)
                .flatMap(repositorio::buscar)
                .map(cartao -> autorizar(cartao, senhaCartao, valorTransacao))
                .orElseGet(() -> ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_INEXISTENTE));
        switch (resultado) {
            case ResultadoAutorizacao.Autorizada autorizada -> LOG.info("transacao.autorizada valor={}", valor);
            case ResultadoAutorizacao.Recusada recusada ->
                    LOG.warn("transacao.recusada motivo={} valor={}", recusada.motivo(), valor);
        }
        return resultado;
    }

    private ResultadoAutorizacao autorizar(Cartao cartao, String senhaCartao, Dinheiro valor) {
        NumeroCartao numero = cartao.numero();
        Instant agora = relogio.instant();
        return bloqueio.verificarDesbloqueado(numero, agora)
                .entao(() -> cartao.conferirSenha(senhaCartao, cifrador)
                        .seRecusada(motivo -> bloqueio.registrarSenhaErrada(numero, agora)))
                .entao(() -> bloqueio.registrarSenhaCerta(numero, agora))
                .entao(() -> cartao.verificarSaldo(valor))
                .entao(() -> repositorio.debitar(numero, valor));
    }
}
