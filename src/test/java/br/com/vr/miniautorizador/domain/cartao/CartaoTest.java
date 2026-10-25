package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartaoTest {

    private static final NumeroCartao NUMERO = new NumeroCartao("6549873025634501");
    private static final String SENHA = "1234";

    private final CifradorSenha cifrador = new CifradorSenhaFake();

    @Test
    @DisplayName("cartão novo nasce com saldo de R$ 500,00")
    void cartaoNovoNasceComSaldoInicial() {
        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifrador);

        assertThat(cartao.saldo()).isEqualTo(Dinheiro.de("500.00"));
        assertThat(cartao.numero()).isEqualTo(NUMERO);
    }

    @Test
    @DisplayName("cartão novo guarda a senha cifrada, cifrando uma única vez")
    void cartaoNovoCifraSenhaUmaUnicaVez() {
        CifradorSenha cifradorMock = mock(CifradorSenha.class);
        when(cifradorMock.cifrar(SENHA)).thenReturn(cifrador.cifrar(SENHA));

        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifradorMock);

        verify(cifradorMock, times(1)).cifrar(SENHA);
        assertThat(cartao.senha().hash()).isNotEqualTo(SENHA);
    }

    @ParameterizedTest(name = "não cria cartão com senha \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void naoCriaCartaoComSenhaVazia(String senha) {
        assertThatThrownBy(() -> Cartao.criar(NUMERO, senha, cifrador))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    @DisplayName("reconstituir preserva o saldo persistido sem aplicar o saldo inicial")
    void reconstituirPreservaSaldo() {
        Cartao cartao = Cartao.reconstituir(NUMERO, cifrador.cifrar(SENHA), Dinheiro.de("12.34"));

        assertThat(cartao.saldo()).isEqualTo(Dinheiro.de("12.34"));
    }

    @Test
    @DisplayName("a senha do cartão confere")
    void senhaCorretaConfere() {
        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifrador);

        assertThat(cartao.conferirSenha(SENHA, cifrador)).isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @ParameterizedTest(name = "senha \"{0}\" é recusada com SENHA_INVALIDA")
    @NullAndEmptySource
    @ValueSource(strings = "4321")
    void senhaDiferenteNaoConfere(String senhaInformada) {
        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifrador);

        assertThat(cartao.conferirSenha(senhaInformada, cifrador))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SENHA_INVALIDA));
    }

    @ParameterizedTest(name = "saldo de 500,00 cobre {0}")
    @ValueSource(strings = {"0.01", "10.00", "500.00"})
    void saldoCobreValor(String valor) {
        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifrador);

        assertThat(cartao.verificarSaldo(Dinheiro.de(valor))).isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @Test
    @DisplayName("saldo de 500,00 não cobre 500,01")
    void saldoNaoCobreValor() {
        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifrador);

        assertThat(cartao.verificarSaldo(Dinheiro.de("500.01")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));
    }

    @Test
    @DisplayName("igualdade do agregado é pela identidade, o número do cartão")
    void igualdadePeloNumero() {
        Cartao a = Cartao.reconstituir(NUMERO, cifrador.cifrar(SENHA), Dinheiro.de("1.00"));
        Cartao b = Cartao.reconstituir(NUMERO, cifrador.cifrar("9999"), Dinheiro.de("2.00"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("cartões com números diferentes não são iguais")
    void desigualdadePeloNumero() {
        Cartao a = Cartao.reconstituir(NUMERO, cifrador.cifrar(SENHA), Dinheiro.de("1.00"));
        Cartao b = Cartao.reconstituir(new NumeroCartao("6549873025634502"), cifrador.cifrar(SENHA), Dinheiro.de("1.00"));

        assertThat(a).isNotEqualTo(b).doesNotHaveSameHashCodeAs(b);
        assertThat(a).isNotEqualTo("outro tipo").isNotEqualTo(null);
    }

    @Test
    @DisplayName("toString não expõe número completo nem senha")
    void toStringNaoExpoeDadosSensiveis() {
        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifrador);

        assertThat(cartao.toString())
                .doesNotContain("6549873025634501")
                .doesNotContain(cartao.senha().hash())
                .contains("4501");
    }
}
