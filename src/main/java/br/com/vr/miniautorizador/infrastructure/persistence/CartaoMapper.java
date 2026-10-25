package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.SenhaCifrada;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;

/**
 * Traduz entre o agregado de domínio e o modelo de persistência.
 * Escrito à mão: o agregado não expõe construtor público nem setters, o que não combina
 * com geradores de mapeamento baseados em convenção.
 */
final class CartaoMapper {

    private CartaoMapper() {
    }

    static CartaoEntity paraEntidade(Cartao cartao) {
        return new CartaoEntity(
                cartao.numero().valor(),
                cartao.senha().hash(),
                cartao.saldo().valor());
    }

    static Cartao paraDominio(CartaoEntity entidade) {
        return Cartao.reconstituir(
                new NumeroCartao(entidade.getNumero()),
                new SenhaCifrada(entidade.getSenha()),
                new Dinheiro(entidade.getSaldo()));
    }
}
