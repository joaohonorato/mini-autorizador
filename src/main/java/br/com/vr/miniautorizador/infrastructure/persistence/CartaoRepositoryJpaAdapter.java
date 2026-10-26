package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.CartaoRepository;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter que implementa a porta {@link CartaoRepository} com Spring Data JPA.
 */
@Component
class CartaoRepositoryJpaAdapter implements CartaoRepository {

    private static final int UMA_LINHA_ALTERADA = 1;

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
     */
    @Override
    public ResultadoCriacao salvarNovo(Cartao cartao) {
        try {
            jpa.saveAndFlush(CartaoMapper.paraEntidade(cartao));
            return ResultadoCriacao.CRIADO;
        } catch (DataIntegrityViolationException e) {
            return ResultadoCriacao.JA_EXISTENTE;
        }
    }

    @Override
    public ResultadoAutorizacao debitar(NumeroCartao numero, Dinheiro valor) {
        int linhasAlteradas = jpa.debitarSeHouverSaldo(numero.valor(), valor.valor());
        return ResultadoAutorizacao.exigir(linhasAlteradas == UMA_LINHA_ALTERADA, MotivoRecusa.SALDO_INSUFICIENTE);
    }
}
