package br.com.vr.miniautorizador.domain.compartilhado;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DinheiroTest {

    @ParameterizedTest(name = "normaliza \"{0}\" para duas casas decimais")
    @CsvSource({
            "10,     10.00",
            "10.5,   10.50",
            "10.500, 10.50",
            "0,      0.00"
    })
    void normalizaParaDuasCasas(String entrada, String esperado) {
        assertThat(Dinheiro.de(entrada).valor().toPlainString()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("valores iguais com escalas diferentes são o mesmo dinheiro")
    void escalasDiferentesSaoIguais() {
        assertThat(Dinheiro.de("10")).isEqualTo(Dinheiro.de("10.00"));
    }

    @ParameterizedTest(name = "rejeita valor {0} em vez de arredondar ou aceitar")
    @CsvSource(nullValues = "null", value = {
            "negativo,               -0.01",
            "com mais de duas casas, 10.005",
            "nulo,                   null"
    })
    void rejeitaValorInvalido(String descricao, BigDecimal valor) {
        assertThatThrownBy(() -> new Dinheiro(valor)).isInstanceOf(ValorInvalidoException.class);
    }

    @ParameterizedTest(name = "rejeita rapidamente o valor {0}, acima do limite de 17 dígitos inteiros")
    @ValueSource(strings = {"1e40000000", "1e999999999", "1e2147483647", "100000000000000000"})
    @Timeout(1)
    void rejeitaValorAcimaDoLimite(BigDecimal valor) {
        assertThatThrownBy(() -> new Dinheiro(valor)).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Dinheiro.positivo(valor)).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    @DisplayName("aceita o maior valor que cabe na coluna DECIMAL(19, 2)")
    void aceitaMaiorValor() {
        assertThat(Dinheiro.de("99999999999999999.99").valor().toPlainString()).isEqualTo("99999999999999999.99");
    }

    @ParameterizedTest(name = "valor de transação {0} não é positivo")
    @ValueSource(strings = {"0", "0.00", "-1"})
    void valorDeTransacaoNaoPositivo(BigDecimal valor) {
        assertThatThrownBy(() -> Dinheiro.positivo(valor)).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    @DisplayName("valor de transação positivo é aceito")
    void valorDeTransacaoPositivo() {
        assertThat(Dinheiro.positivo(new BigDecimal("0.01"))).isEqualTo(Dinheiro.de("0.01"));
    }

    @ParameterizedTest(name = "saldo {0} cobre {1}: {2}")
    @CsvSource({
            "10.00, 9.99,  true",
            "10.00, 10.00, true",
            "10.00, 10.01, false",
            "0.00,  0.01,  false"
    })
    void saldoCobreValor(String saldo, String valor, boolean esperado) {
        assertThat(Dinheiro.de(saldo).cobre(Dinheiro.de(valor))).isEqualTo(esperado);
    }
}
