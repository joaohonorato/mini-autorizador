package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;

import java.util.Optional;

/**
 * Porta de persistência do agregado {@link Cartao}.
 */
public interface CartaoRepository {

    Optional<Cartao> buscar(NumeroCartao numero);

    /**
     * Insere o cartão se o número ainda não estiver cadastrado. Deve ser seguro sob
     * concorrência: duas criações simultâneas do mesmo número resultam em um único cartão.
     */
    ResultadoCriacao salvarNovo(Cartao cartao);

    /**
     * Debita o valor de forma atômica, apenas se o saldo persistido ainda cobrir o valor.
     * É a garantia final contra débitos concorrentes vindos de instâncias diferentes.
     *
     * @return autorizada se o débito foi aplicado; recusada com SALDO_INSUFICIENTE se o saldo
     *         persistido não cobria o valor
     */
    ResultadoAutorizacao debitar(NumeroCartao numero, Dinheiro valor);
}
