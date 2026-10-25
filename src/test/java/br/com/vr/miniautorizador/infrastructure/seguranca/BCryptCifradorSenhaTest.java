package br.com.vr.miniautorizador.infrastructure.seguranca;

import br.com.vr.miniautorizador.domain.cartao.SenhaCifrada;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptCifradorSenhaTest {

    private final BCryptCifradorSenha cifrador = new BCryptCifradorSenha();

    @Test
    @DisplayName("hash usa BCrypt e não contém a senha")
    void hashUsaBCrypt() {
        SenhaCifrada cifrada = cifrador.cifrar("1234");

        assertThat(cifrada.hash()).startsWith("$2a$").doesNotContain("1234");
    }

    @Test
    @DisplayName("mesma senha gera hashes diferentes por causa do salt")
    void mesmaSenhaGeraHashesDiferentes() {
        assertThat(cifrador.cifrar("1234")).isNotEqualTo(cifrador.cifrar("1234"));
    }

    @ParameterizedTest(name = "confere \"{0}\" com o hash de \"1234\": {1}")
    @CsvSource({
            "1234, true",
            "1235, false"
    })
    void confereSenha(String tentativa, boolean esperado) {
        SenhaCifrada cifrada = cifrador.cifrar("1234");

        assertThat(cifrador.confere(tentativa, cifrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("aceita senha de exatamente 72 bytes, o limite do BCrypt")
    void aceitaSenhaNoLimite() {
        String senha = "a".repeat(72);

        assertThat(cifrador.confere(senha, cifrador.cifrar(senha))).isTrue();
    }

    @ParameterizedTest(name = "rejeita senha acima de 72 bytes: {0}")
    @CsvSource({
            "73 letras ASCII,                  73, a",
            "37 caracteres de 2 bytes em UTF-8, 37, é"
    })
    void rejeitaSenhaAcimaDoLimite(String descricao, int repeticoes, String caractere) {
        String senha = caractere.repeat(repeticoes);

        assertThatThrownBy(() -> cifrador.cifrar(senha)).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    @DisplayName("senha que só difere depois do 72º byte não confere")
    void senhaQueDifereDepoisDoLimiteNaoConfere() {
        SenhaCifrada cifrada = cifrador.cifrar("a".repeat(72));

        assertThat(cifrador.confere("a".repeat(72) + "XYZ", cifrada)).isFalse();
    }
}
