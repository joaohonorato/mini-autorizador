package br.com.vr.miniautorizador.domain.transacao;

/**
 * A chave já foi usada por uma requisição diferente (outro cartão ou outro valor). Reenviar a mesma
 * compra repete a chave; uma compra nova precisa de chave nova. A API a traduz para 422.
 */
public class ChaveIdempotenciaReutilizadaException extends RuntimeException {

    public ChaveIdempotenciaReutilizadaException() {
        super("Idempotency-Key já foi usada em outra requisição");
    }
}
