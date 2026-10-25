package br.com.vr.miniautorizador.api;

import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lê a coluna {@code senha} direto do MySQL depois de um cadastro pelo HTTP, com o cifrador real
 * da aplicação: o que o README afirma sobre o BCrypt vale para o que de fato fica gravado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestcontainer.class)
class SenhaNoBancoIT {

    private static final String NUMERO = "5400000000000001";
    private static final String SENHA = "1234";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a senha gravada no banco é um hash BCrypt, não o texto original")
    void senhaNoBancoEHashBcrypt() {
        assertThat(rest.withBasicAuth("username", "password")
                .postForEntity("/cartoes", Map.of("numeroCartao", NUMERO, "senha", SENHA), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String senhaNoBanco = jdbc.queryForObject("SELECT senha FROM cartao WHERE numero = ?", String.class, NUMERO);

        assertThat(senhaNoBanco)
                .matches("\\$2[aby]\\$\\d{2}\\$.{53}")
                .doesNotContain(SENHA);
    }
}
