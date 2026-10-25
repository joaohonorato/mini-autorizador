package br.com.vr.miniautorizador.domain.cartao;

/**
 * Porta para o algoritmo de cifragem de senhas. O domínio define o contrato;
 * a infraestrutura escolhe o algoritmo.
 */
public interface CifradorSenha {

    /**
     * @throws br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException se a senha
     *         não puder ser cifrada sem perda, por exemplo por exceder o limite do algoritmo
     */
    SenhaCifrada cifrar(String senhaAberta);

    /**
     * Deve conferir a senha inteira: uma tentativa só confere se for idêntica à senha cifrada.
     */
    boolean confere(String senhaAberta, SenhaCifrada senhaCifrada);
}
