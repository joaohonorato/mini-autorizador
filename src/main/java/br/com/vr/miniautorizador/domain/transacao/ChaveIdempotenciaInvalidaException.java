package br.com.vr.miniautorizador.domain.transacao;

/**
 * A chave de idempotência não tem o formato aceito. A API a traduz para 400.
 */
public class ChaveIdempotenciaInvalidaException extends RuntimeException {

    public ChaveIdempotenciaInvalidaException(String message) {
        super(message);
    }
}
