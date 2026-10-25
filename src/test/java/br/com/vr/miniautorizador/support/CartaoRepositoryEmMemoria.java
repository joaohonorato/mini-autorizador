package br.com.vr.miniautorizador.support;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.CartaoRepository;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação em memória da porta, com a mesma semântica do adapter JPA:
 * inserção só se o número for novo e débito condicional atômico.
 */
public class CartaoRepositoryEmMemoria implements CartaoRepository {

    private final Map<NumeroCartao, Cartao> cartoes = new HashMap<>();

    @Override
    public synchronized Optional<Cartao> buscar(NumeroCartao numero) {
        return Optional.ofNullable(cartoes.get(numero));
    }

    @Override
    public synchronized ResultadoCriacao salvarNovo(Cartao cartao) {
        return cartoes.putIfAbsent(cartao.numero(), cartao) == null
                ? ResultadoCriacao.CRIADO
                : ResultadoCriacao.JA_EXISTENTE;
    }

    @Override
    public synchronized ResultadoAutorizacao debitar(NumeroCartao numero, Dinheiro valor) {
        return Optional.ofNullable(cartoes.get(numero))
                .filter(cartao -> cartao.saldo().cobre(valor))
                .map(atual -> {
                    Dinheiro novoSaldo = new Dinheiro(atual.saldo().valor().subtract(valor.valor()));
                    cartoes.put(numero, Cartao.reconstituir(numero, atual.senha(), novoSaldo));
                    return ResultadoAutorizacao.autorizada();
                })
                .orElseGet(() -> ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));
    }

    /**
     * Permite simular, nos testes, outra instância debitando o saldo entre a leitura e o débito.
     */
    public synchronized void substituir(Cartao cartao) {
        cartoes.put(cartao.numero(), cartao);
    }
}
