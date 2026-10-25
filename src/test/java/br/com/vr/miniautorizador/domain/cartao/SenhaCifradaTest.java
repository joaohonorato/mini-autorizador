package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SenhaCifradaTest {

    @ParameterizedTest(name = "rejeita hash \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejeitaHashVazio(String hash) {
        assertThatThrownBy(() -> new SenhaCifrada(hash)).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    @DisplayName("toString não expõe o hash")
    void toStringNaoExpoeHash() {
        assertThat(new SenhaCifrada("$2a$10$abc")).hasToString("SenhaCifrada[****]");
    }
}
