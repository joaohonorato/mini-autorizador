package br.com.vr.miniautorizador.api;

import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O bloqueio por senhas erradas ligado pela configuração, de ponta a ponta pelo HTTP. O padrão
 * (desligado) é coberto pelo cenário Cucumber, que roda com a configuração padrão da aplicação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "autorizador.bloqueio-senha.ativo=true")
@ImportTestcontainers(MySqlTestcontainer.class)
class BloqueioPorSenhaApiIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("3 senhas erradas bloqueiam o cartão, e nem a senha certa passa enquanto ele está bloqueado")
    void tresSenhasErradasBloqueiam() {
        String numero = "5100000000000001";
        cadastrar(numero);

        for (String errada : new String[]{"0000", "0001", "0002"}) {
            ResponseEntity<String> resposta = transacao(numero, errada);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).isEqualTo("SENHA_INVALIDA");
        }

        ResponseEntity<String> comSenhaCerta = transacao(numero, "1234");
        assertThat(comSenhaCerta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(comSenhaCerta.getBody()).isEqualTo("CARTAO_BLOQUEADO");
        assertThat(saldo(numero)).isEqualTo("500.00");
    }

    @Test
    @DisplayName("a senha certa zera a contagem: 2 erradas, 1 certa e mais 2 erradas não bloqueiam")
    void senhaCertaZeraContagem() {
        String numero = "5100000000000002";
        cadastrar(numero);

        transacao(numero, "0000");
        transacao(numero, "0001");
        assertThat(transacao(numero, "1234").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        transacao(numero, "0002");
        transacao(numero, "0003");

        ResponseEntity<String> resposta = transacao(numero, "1234");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).isEqualTo("OK");
        assertThat(saldo(numero)).isEqualTo("480.00");
    }

    @Test
    @DisplayName("cartão bloqueado e sem saldo recusa por CARTAO_BLOQUEADO, não por SALDO_INSUFICIENTE")
    void bloqueioVemAntesDoSaldo() {
        String numero = "5100000000000003";
        cadastrar(numero);
        autenticado().postForEntity("/transacoes",
                Map.of("numeroCartao", numero, "senhaCartao", "1234", "valor", new BigDecimal("500.00")), String.class);
        assertThat(saldo(numero)).isEqualTo("0.00");
        for (String errada : new String[]{"0000", "0001", "0002"}) {
            transacao(numero, errada);
        }

        ResponseEntity<String> resposta = transacao(numero, "1234");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).isEqualTo("CARTAO_BLOQUEADO");
    }

    private void cadastrar(String numero) {
        ResponseEntity<String> resposta = autenticado().postForEntity(
                "/cartoes", Map.of("numeroCartao", numero, "senha", "1234"), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> transacao(String numero, String senha) {
        return autenticado().postForEntity("/transacoes",
                Map.of("numeroCartao", numero, "senhaCartao", senha, "valor", new BigDecimal("10.00")), String.class);
    }

    private String saldo(String numero) {
        return autenticado().getForObject("/cartoes/" + numero, String.class);
    }

    private TestRestTemplate autenticado() {
        return rest.withBasicAuth("username", "password");
    }
}
