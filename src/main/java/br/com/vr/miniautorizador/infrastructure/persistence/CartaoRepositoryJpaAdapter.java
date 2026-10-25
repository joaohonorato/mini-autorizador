package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.CartaoRepository;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter que implementa a porta {@link CartaoRepository} com Spring Data JPA.
 */
@Component
class CartaoRepositoryJpaAdapter implements CartaoRepository {

    private static final int UMA_LINHA_ALTERADA = 1;
    private static final int TENTATIVAS_EM_DEADLOCK = 3;

    private final CartaoJpaRepository jpa;

    CartaoRepositoryJpaAdapter(CartaoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Cartao> buscar(NumeroCartao numero) {
        return jpa.findById(numero.valor()).map(CartaoMapper::paraDominio);
    }

    /**
     * A chave primária garante a unicidade. Em vez de consultar antes de inserir, o que abriria
     * uma janela de corrida, tenta inserir e trata a violação da chave como cartão já existente.
     *
     * <p>Se o número acabou de ser apagado, inserções simultâneas dele disputam a linha marcada
     * como apagada, e o InnoDB pode desfazer uma delas por deadlock. Nesse caso a inserção é
     * repetida, como o próprio MySQL orienta, e a nova tentativa resulta em criado ou já existente.</p>
     */
    @Override
    public ResultadoCriacao salvarNovo(Cartao cartao) {
        return inserir(cartao, TENTATIVAS_EM_DEADLOCK);
    }

    private ResultadoCriacao inserir(Cartao cartao, int tentativasRestantes) {
        try {
            jpa.saveAndFlush(CartaoMapper.paraEntidade(cartao));
            return ResultadoCriacao.CRIADO;
        } catch (DataIntegrityViolationException e) {
            return ResultadoCriacao.JA_EXISTENTE;
        } catch (PessimisticLockingFailureException deadlock) {
            return Optional.of(tentativasRestantes - 1)
                    .filter(restantes -> restantes > 0)
                    .map(restantes -> inserir(cartao, restantes))
                    .orElseThrow(() -> deadlock);
        }
    }

    @Override
    public ResultadoAutorizacao debitar(NumeroCartao numero, Dinheiro valor) {
        int linhasAlteradas = jpa.debitarSeHouverSaldo(numero.valor(), valor.valor());
        return ResultadoAutorizacao.exigir(linhasAlteradas == UMA_LINHA_ALTERADA, MotivoRecusa.SALDO_INSUFICIENTE);
    }
}
