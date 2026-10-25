package br.com.vr.miniautorizador.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcContextoTest {

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
        assertThat(MdcContexto.mascarar(numero)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "\"{0}\" vira ****")
    @NullSource
    @ValueSource(strings = {"", "1", "123"})
    void numeroCurtoOuAusenteViraAsteriscos(String numero) {
        assertThat(MdcContexto.mascarar(numero)).isEqualTo("****");
    }

    @Test
    @DisplayName("o cartão entra no contexto de log mascarado e sai ao limpar")
    void entraMascaradoESai() {
        MdcContexto.porCartao("6549873025634501");

        assertThat(MDC.get("numeroCartao")).isEqualTo("************4501");

        MdcContexto.limpar();

        assertThat(MDC.get("numeroCartao")).isNull();
    }
}
