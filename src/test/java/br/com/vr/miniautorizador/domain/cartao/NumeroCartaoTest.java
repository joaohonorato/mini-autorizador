package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumeroCartaoTest {

    @ParameterizedTest(name = "aceita número {0}")
    @ValueSource(strings = {"6549873025634501", "1234567890123456"})
    void aceitaNumeroDe16Digitos(String numero) {
        assertThat(new NumeroCartao(numero).valor()).isEqualTo(numero);
    }

    @Test
    @DisplayName("preserva zeros à esquerda")
    void preservaZerosAEsquerda() {
        assertThat(new NumeroCartao("0000873025634501").valor()).isEqualTo("0000873025634501");
    }

    @ParameterizedTest(name = "rejeita número {0}")
    @CsvSource(nullValues = "null", value = {
            "nulo,           null",
            "vazio,          ''",
            "com 13 dígitos, 1234567890123",
            "com 15 dígitos, 654987302563450",
            "com 17 dígitos, 65498730256345012",
            "com 19 dígitos, 1234567890123456789",
            "com letra,      654987302563450A",
            "com espaços,    6549 8730 2563 4501",
            "com hífens,     6549-8730-2563-4501"
    })
    void rejeitaNumeroInvalido(String descricao, String numero) {
        assertThatThrownBy(() -> new NumeroCartao(numero)).isInstanceOf(ValorInvalidoException.class);
    }

    @ParameterizedTest(name = "tentar devolve vazio para \"{0}\" sem lançar exceção")
    @NullSource
    @ValueSource(strings = {"abc", "123"})
    void tentarDevolveVazioParaFormatoInvalido(String numero) {
        assertThat(NumeroCartao.tentar(numero)).isEmpty();
    }

    @Test
    @DisplayName("tentar devolve o número quando o formato é válido")
    void tentarDevolveNumeroValido() {
        assertThat(NumeroCartao.tentar("6549873025634501")).contains(new NumeroCartao("6549873025634501"));
    }

    @Test
    @DisplayName("toString mascara tudo menos os quatro últimos dígitos")
    void toStringMascaraNumero() {
        assertThat(new NumeroCartao("6549873025634501")).hasToString("************4501");
    }
}
