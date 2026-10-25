package br.com.vr.miniautorizador.api;

import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestcontainer.class)
class ValidacaoHttpIT {

    private static final String SENHA = "1234";
    private static final String CARTAO_FORMATO_VALIDO = "5400000000000001";
    private static final String CARTAO_VALORES = "5400000000000002";
    private static final String CARTAO_SALDO_EXATO = "5400000000000003";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("cartão com formato inválido é rejeitado no cadastro, consulta e transação pelo HTTP real")
    void numeroCartaoInvalidoEmTodosOsEndpoints() {
        List<String> numerosInvalidos = List.of(
                "540000000000001",
                "54000000000000012",
                "54000000000000a1",
                "54000000000000 1",
                "5400-0000-0000-001");

        for (String numero : numerosInvalidos) {
            ResponseEntity<String> cadastro = autenticado().postForEntity(
                    "/cartoes", Map.of("numeroCartao", numero, "senha", SENHA), String.class);
            assertThat(cadastro.getStatusCode()).as("cadastro do cartão %s", numero)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(cadastro.getBody()).as("detalhe do cadastro do cartão %s", numero)
                    .contains("Número do cartão deve ter 16 dígitos");

            ResponseEntity<String> consulta = autenticado().getForEntity(
                    "/cartoes/{numero}", String.class, numero);
            assertThat(consulta.getStatusCode()).as("consulta do cartão %s", numero)
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(consulta.getBody()).as("corpo da consulta do cartão %s", numero).isNull();

            ResponseEntity<String> transacao = transacao(numero, "1.00");
            assertThat(transacao.getStatusCode()).as("transação do cartão %s", numero)
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(transacao.getBody()).as("motivo da transação do cartão %s", numero)
                    .isEqualTo("CARTAO_INEXISTENTE");
        }
    }

    @Test
    @DisplayName("valores com mais de duas casas são rejeitados sem débito e um centavo é debitado exatamente")
    void precisaoDecimalPeloHttpReal() {
        cadastrar(CARTAO_VALORES);

        for (String valor : List.of("0.001", "10.005")) {
            ResponseEntity<String> resposta = transacao(CARTAO_VALORES, valor);
            assertThat(resposta.getStatusCode()).as("valor %s", valor).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(saldo(CARTAO_VALORES)).as("saldo após rejeitar %s", valor).isEqualTo("500.00");
        }

        ResponseEntity<String> umCentavo = transacao(CARTAO_VALORES, "0.01");
        assertThat(umCentavo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(umCentavo.getBody()).isEqualTo("OK");
        assertThat(saldo(CARTAO_VALORES)).isEqualTo("499.99");
    }

    @Test
    @DisplayName("transação igual ao saldo zera o saldo e o centavo seguinte é recusado")
    void valorIgualAoSaldoPeloHttpReal() {
        cadastrar(CARTAO_SALDO_EXATO);

        ResponseEntity<String> saldoInteiro = transacao(CARTAO_SALDO_EXATO, "500.00");
        assertThat(saldoInteiro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(saldoInteiro.getBody()).isEqualTo("OK");
        assertThat(saldo(CARTAO_SALDO_EXATO)).isEqualTo("0.00");

        ResponseEntity<String> umCentavo = transacao(CARTAO_SALDO_EXATO, "0.01");
        assertThat(umCentavo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(umCentavo.getBody()).isEqualTo("SALDO_INSUFICIENTE");
        assertThat(saldo(CARTAO_SALDO_EXATO)).isEqualTo("0.00");
    }

    @Test
    @DisplayName("chave repetida no JSON responde 400 e não deixa efeito colateral")
    void chaveRepetidaNoJsonResponde400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String cartao = "5400000000000004";

        ResponseEntity<String> cadastro = autenticado().postForEntity("/cartoes", new HttpEntity<>(
                "{\"numeroCartao\":\"%s\",\"senha\":\"1234\",\"senha\":\"9999\"}".formatted(cartao), headers),
                String.class);
        assertThat(cadastro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(autenticado().getForEntity("/cartoes/{numero}", String.class, cartao).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        cadastrar(cartao);
        ResponseEntity<String> transacao = autenticado().postForEntity("/transacoes", new HttpEntity<>(
                "{\"numeroCartao\":\"%s\",\"senhaCartao\":\"%s\",\"valor\":1,\"valor\":2}".formatted(cartao, SENHA),
                headers), String.class);
        assertThat(transacao.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(saldo(cartao)).isEqualTo("500.00");
    }

    private void cadastrar(String numero) {
        ResponseEntity<String> resposta = autenticado().postForEntity(
                "/cartoes", Map.of("numeroCartao", numero, "senha", SENHA), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> transacao(String numero, String valor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String corpo = """
                {"numeroCartao":"%s","senhaCartao":"%s","valor":%s}
                """.formatted(numero, SENHA, valor);
        return autenticado().postForEntity("/transacoes", new HttpEntity<>(corpo, headers), String.class);
    }

    private String saldo(String numero) {
        return autenticado().getForObject("/cartoes/{numero}", String.class, numero);
    }

    private TestRestTemplate autenticado() {
        return rest.withBasicAuth("username", "password");
    }
}
