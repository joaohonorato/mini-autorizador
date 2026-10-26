package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.support.CartaoRepositoryEmMemoria;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultarSaldoTest {

    private static final String NUMERO = "6549873025634501";

    private final CartaoRepositoryEmMemoria repositorio = new CartaoRepositoryEmMemoria();
    private final ConsultarSaldo consultarSaldo = new ConsultarSaldo(repositorio);

    @Test
    @DisplayName("consulta de cartão cadastrado devolve o saldo")
    void consultaDevolveSaldo() {
        new CriarCartao(repositorio, new CifradorSenhaFake()).executar(NUMERO, "1234");

        assertThat(consultarSaldo.executar(NUMERO)).contains(Dinheiro.de("500.00"));
    }

    @ParameterizedTest(name = "consulta de \"{0}\" não encontra saldo")
    @ValueSource(strings = {NUMERO, "abc", "654987302563450", "65498730256345012", "6549 8730 2563 4501",
            "6549-8730-2563-4501", "654987302563450A"})
    void consultaNaoEncontraSaldo(String numero) {
        assertThat(consultarSaldo.executar(numero)).isEmpty();
    }
}
