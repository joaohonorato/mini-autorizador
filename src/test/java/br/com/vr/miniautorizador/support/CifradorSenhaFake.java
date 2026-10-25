package br.com.vr.miniautorizador.support;

import br.com.vr.miniautorizador.domain.cartao.CifradorSenha;
import br.com.vr.miniautorizador.domain.cartao.SenhaCifrada;

/**
 * Cifrador determinístico e rápido para testes de domínio e aplicação.
 * Comporta-se como um cifrador real: o hash é diferente da senha e só confere com a original.
 */
public class CifradorSenhaFake implements CifradorSenha {

    private static final String PREFIXO = "fake-hash:";

    @Override
    public SenhaCifrada cifrar(String senhaAberta) {
        return new SenhaCifrada(PREFIXO + new StringBuilder(senhaAberta).reverse());
    }

    @Override
    public boolean confere(String senhaAberta, SenhaCifrada senhaCifrada) {
        return cifrar(senhaAberta).equals(senhaCifrada);
    }
}
