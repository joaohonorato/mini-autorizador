package br.com.vr.miniautorizador.domain.transacao;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Chave que o cliente gera para uma tentativa de compra e repete nos reenvios dela. Tem de 1 a 64
 * caracteres entre letras, dígitos, ponto, sublinhado, dois-pontos e hífen, o que cobre um UUID.
 */
public record ChaveIdempotencia(String valor) {

    private static final Pattern FORMATO = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    public ChaveIdempotencia {
        valor = Optional.ofNullable(valor)
                .filter(v -> FORMATO.matcher(v).matches())
                .orElseThrow(() -> new ChaveIdempotenciaInvalidaException(
                        "Idempotency-Key deve ter de 1 a 64 caracteres entre letras, dígitos, '.', '_', ':' e '-'"));
    }
}
