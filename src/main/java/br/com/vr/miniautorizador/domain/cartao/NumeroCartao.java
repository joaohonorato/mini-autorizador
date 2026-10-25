package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Número do cartão (PAN). Mantido como texto para preservar zeros à esquerda.
 * O formato aceito é de exatamente 16 dígitos numéricos, sem validar o dígito verificador (Luhn).
 */
public record NumeroCartao(String valor) {

    private static final Predicate<String> FORMATO_VALIDO = Pattern.compile("\\d{16}").asMatchPredicate();
    private static final int DIGITOS_VISIVEIS = 4;

    public NumeroCartao {
        valor = Optional.ofNullable(valor)
                .filter(FORMATO_VALIDO)
                .orElseThrow(() -> new ValorInvalidoException("Número do cartão deve ter 16 dígitos"));
    }

    /**
     * Converte sem lançar exceção. Um número em formato inválido não pode corresponder
     * a nenhum cartão cadastrado, então consultas o tratam como cartão inexistente.
     */
    public static Optional<NumeroCartao> tentar(String valor) {
        return Optional.ofNullable(valor)
                .filter(FORMATO_VALIDO)
                .map(NumeroCartao::new);
    }

    /**
     * Mascarado para que o número completo nunca vaze em logs.
     */
    @Override
    public String toString() {
        return "*".repeat(valor.length() - DIGITOS_VISIVEIS) + valor.substring(valor.length() - DIGITOS_VISIVEIS);
    }
}
