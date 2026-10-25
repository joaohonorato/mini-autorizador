package br.com.vr.miniautorizador.domain.compartilhado;

/**
 * Lançada quando um value object recebe um valor que viola suas regras de formação.
 */
public class ValorInvalidoException extends RuntimeException {

    public ValorInvalidoException(String mensagem) {
        super(mensagem);
    }
}
