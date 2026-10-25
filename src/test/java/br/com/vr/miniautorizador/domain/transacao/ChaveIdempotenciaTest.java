package br.com.vr.miniautorizador.domain.transacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaveIdempotenciaTest {

    @ParameterizedTest(name = "aceita \"{0}\"")
    @ValueSource(strings = {"a", "compra-1", "3f2b8c1e-9d4a-4e7b-8a61-5c0d2e9f7b34", "loja.1:caixa_2",
            "0123456789012345678901234567890123456789012345678901234567890123"})
    void aceitaChavesBemFormadas(String valor) {
        assertThat(new ChaveIdempotencia(valor).valor()).isEqualTo(valor);
    }

    @ParameterizedTest(name = "recusa \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "com espaço", "acentuação", "a/b", "chave\n",
            "01234567890123456789012345678901234567890123456789012345678901234"})
    void recusaChavesForaDoFormato(String valor) {
        assertThatThrownBy(() -> new ChaveIdempotencia(valor))
                .isInstanceOf(ChaveIdempotenciaInvalidaException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    @DisplayName("duas chaves com o mesmo texto são iguais")
    void igualdadePorValor() {
        assertThat(new ChaveIdempotencia("compra-1")).isEqualTo(new ChaveIdempotencia("compra-1"));
    }
}
