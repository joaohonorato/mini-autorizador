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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.vr.miniautorizador.support.ExecucaoParalela.emParalelo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * O cabeçalho Idempotency-Key de ponta a ponta pelo HTTP, com o bloqueio por senhas erradas ligado
 * para provar que o reenvio de uma senha errada não conta como outra tentativa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "autorizador.bloqueio-senha.ativo=true")
@ImportTestcontainers(MySqlTestcontainer.class)
class IdempotenciaApiIT {

    private static final String REPETIDA = "Idempotent-Replayed";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("o reenvio devolve 201 OK marcado como repetido e debita uma vez só")
    void reenvioNaoDebitaDeNovo() {
        String numero = cadastrar("5300000000000001");

        ResponseEntity<String> primeira = transacao("compra-reenvio", numero, "1234", "10.00");
        ResponseEntity<String> reenvio = transacao("compra-reenvio", numero, "1234", "10.00");

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(primeira.getBody()).isEqualTo("OK");
        assertThat(primeira.getHeaders().containsKey(REPETIDA)).isFalse();
        assertThat(reenvio.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reenvio.getBody()).isEqualTo("OK");
        assertThat(reenvio.getHeaders().getFirst(REPETIDA)).isEqualTo("true");
        assertThat(saldo(numero)).isEqualTo("490.00");
    }

    @Test
    @DisplayName("o reenvio de uma recusa devolve a mesma recusa")
    void reenvioDeRecusa() {
        String numero = cadastrar("5300000000000002");

        ResponseEntity<String> primeira = transacao("compra-recusa", numero, "1234", "500.01");
        ResponseEntity<String> reenvio = transacao("compra-recusa", numero, "1234", "500.01");

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(primeira.getBody()).isEqualTo("SALDO_INSUFICIENTE");
        assertThat(reenvio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(reenvio.getBody()).isEqualTo("SALDO_INSUFICIENTE");
        assertThat(reenvio.getHeaders().getFirst(REPETIDA)).isEqualTo("true");
    }

    @Test
    @DisplayName("reenviar uma senha errada não conta outra tentativa e não bloqueia o cartão")
    void reenvioDeSenhaErradaNaoContaTentativa() {
        String numero = cadastrar("5300000000000003");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> resposta = transacao("compra-senha", numero, "0000", "10.00");
            assertThat(resposta.getBody()).isEqualTo("SENHA_INVALIDA");
        }

        ResponseEntity<String> comSenhaCerta = transacao("compra-nova", numero, "1234", "10.00");
        assertThat(comSenhaCerta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(saldo(numero)).isEqualTo("490.00");
    }

    @Test
    @DisplayName("sem Idempotency-Key cada envio é uma transação nova")
    void semChaveCadaEnvioDebita() {
        String numero = cadastrar("5300000000000004");

        transacao(null, numero, "1234", "10.00");
        ResponseEntity<String> segunda = transacao(null, numero, "1234", "10.00");

        assertThat(segunda.getHeaders().containsKey(REPETIDA)).isFalse();
        assertThat(saldo(numero)).isEqualTo("480.00");
    }

    @Test
    @DisplayName("a mesma chave com outro valor devolve 422 e não debita")
    void chaveReutilizada() {
        String numero = cadastrar("5300000000000005");
        transacao("compra-diverge", numero, "1234", "10.00");

        ResponseEntity<String> outraRequisicao = transacao("compra-diverge", numero, "1234", "20.00");

        assertThat(outraRequisicao.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(outraRequisicao.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(outraRequisicao.getBody()).contains("Idempotency-Key já foi usada em outra requisição");
        assertThat(saldo(numero)).isEqualTo("490.00");
    }

    @Test
    @DisplayName("Idempotency-Key fora do formato devolve 400")
    void chaveInvalida() {
        String numero = cadastrar("5300000000000006");

        ResponseEntity<String> resposta = transacao("chave com espaço", numero, "1234", "10.00");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("Idempotency-Key deve ter de 1 a 64 caracteres");
        assertThat(saldo(numero)).isEqualTo("500.00");
    }

    @Test
    @DisplayName("20 envios simultâneos da mesma compra debitam uma vez só")
    void envioSimultaneoDebitaUmaVez() throws Exception {
        String numero = cadastrar("5300000000000007");

        List<ResponseEntity<String>> respostas = emParalelo(20, () -> transacao("compra-corrida", numero, "1234", "10.00"));

        assertThat(respostas).allSatisfy(r -> {
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(r.getBody()).isEqualTo("OK");
        });
        assertThat(respostas).filteredOn(r -> !r.getHeaders().containsKey(REPETIDA)).hasSize(1);
        assertThat(saldo(numero)).isEqualTo("490.00");
    }

    private String cadastrar(String numero) {
        ResponseEntity<String> resposta = autenticado().postForEntity(
                "/cartoes", Map.of("numeroCartao", numero, "senha", "1234"), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return numero;
    }

    private ResponseEntity<String> transacao(String chave, String numero, String senha, String valor) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        Optional.ofNullable(chave).ifPresent(k -> cabecalhos.set("Idempotency-Key", k));
        Map<String, Object> corpo = Map.of("numeroCartao", numero, "senhaCartao", senha, "valor", new BigDecimal(valor));
        return autenticado().postForEntity("/transacoes", new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private String saldo(String numero) {
        return autenticado().getForObject("/cartoes/" + numero, String.class);
    }

    private TestRestTemplate autenticado() {
        return rest.withBasicAuth("username", "password");
    }
}
