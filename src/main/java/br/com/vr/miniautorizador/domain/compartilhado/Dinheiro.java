package br.com.vr.miniautorizador.domain.compartilhado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Value object monetário em reais, sempre com escala 2 e nunca negativo.
 * Valores com mais de duas casas decimais são rejeitados em vez de arredondados,
 * para que nenhum centavo seja criado ou perdido silenciosamente.
 *
 * <p>A parte inteira vai até 17 dígitos, o que cabe na coluna {@code DECIMAL(19, 2)}. O limite é
 * verificado antes de qualquer cálculo: um valor como {@code 1e40000000} tem representação curta,
 * mas normalizá-lo para duas casas exigiria montar um número com milhões de dígitos.</p>
 */
public record Dinheiro(BigDecimal valor) {

    private static final int ESCALA = 2;
    private static final int DIGITOS_INTEIROS = 17;

    public Dinheiro {
        valor = Optional.ofNullable(valor)
                .filter(v -> v.signum() >= 0)
                .filter(v -> digitosInteiros(v) <= DIGITOS_INTEIROS)
                .filter(v -> v.stripTrailingZeros().scale() <= ESCALA)
                .map(v -> v.setScale(ESCALA, RoundingMode.UNNECESSARY))
                .orElseThrow(() -> new ValorInvalidoException(
                        "Valor monetário deve ser não negativo, ter no máximo duas casas decimais "
                                + "e até " + DIGITOS_INTEIROS + " dígitos inteiros"));
    }

    /**
     * Calculado em {@code long}: com expoentes extremos, a subtração em {@code int} transbordaria
     * e um valor gigantesco pareceria pequeno.
     */
    private static long digitosInteiros(BigDecimal valor) {
        return (long) valor.precision() - valor.scale();
    }

    public static Dinheiro de(String valor) {
        return new Dinheiro(new BigDecimal(valor));
    }

    /**
     * Cria um valor estritamente positivo, usado para o valor de uma transação.
     */
    public static Dinheiro positivo(BigDecimal valor) {
        return Optional.of(new Dinheiro(valor))
                .filter(d -> d.valor.signum() > 0)
                .orElseThrow(() -> new ValorInvalidoException("Valor da transação deve ser maior que zero"));
    }

    public boolean cobre(Dinheiro outro) {
        return valor.compareTo(outro.valor) >= 0;
    }
}
