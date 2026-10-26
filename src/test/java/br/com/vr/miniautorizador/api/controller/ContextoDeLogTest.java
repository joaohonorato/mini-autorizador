package br.com.vr.miniautorizador.api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoDeLogTest {

    @AfterEach
    void limpar() {
        MDC.clear();
    }

    @ParameterizedTest(name = "\"{0}\" é mascarado como \"{1}\"")
    @CsvSource({
            "6549873025634501, ************4501",
            "1234, 1234",
            "12345, *2345"
    })
    void mostraSoOsQuatroUltimosDigitos(String numero, String esperado) {
        assertThat(ContextoDeLog.mascarar(numero)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "\"{0}\" vira ****")
    @NullSource
    @ValueSource(strings = {"", "1", "123"})
    void numeroCurtoOuAusenteViraAsteriscos(String numero) {
        assertThat(ContextoDeLog.mascarar(numero)).isEqualTo("****");
    }

    @Test
    @DisplayName("o cartão entra no contexto de log mascarado")
    void entraMascarado() {
        ContextoDeLog.porCartao("6549873025634501");

        assertThat(MDC.get("numeroCartao")).isEqualTo("************4501");
    }
}
