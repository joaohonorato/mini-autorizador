package br.com.vr.miniautorizador.aceitacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passos dos cenários em autorizacao.feature. O cucumber-spring cria uma instância nova
 * por cenário, então o estado guardado aqui não vaza entre cenários.
 */
public class AutorizacaoSteps {

    private static final String USUARIO = "username";
    private static final String SENHA_API = "password";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private ResponseEntity<String> ultimaResposta;
    private final List<ResponseEntity<String>> respostasTransacoes = new ArrayList<>();

    @Dado("que o cartão {string} está cadastrado com a senha {string}")
    public void queOCartaoEstaCadastrado(String numero, String senha) {
        ResponseEntity<String> resposta = cadastrar(numero, senha);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Quando("eu cadastro o cartão {string} com a senha {string}")
    public void euCadastroOCartao(String numero, String senha) {
        ultimaResposta = cadastrar(numero, senha);
    }

    @Quando("eu faço uma transação de {bigdecimal} no cartão {string} com a senha {string}")
    public void euFacoUmaTransacao(BigDecimal valor, String numero, String senha) {
        ultimaResposta = transacao(numero, senha, valor);
    }

    @Quando("eu faço uma transação de {bigdecimal} no cartão {string} com a senha {string} usando a chave {string}")
    public void euFacoUmaTransacaoComChave(BigDecimal valor, String numero, String senha, String chave) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("Idempotency-Key", chave);
        ultimaResposta = autenticado().postForEntity("/transacoes",
                new HttpEntity<>(Map.of("numeroCartao", numero, "senhaCartao", senha, "valor", valor), cabecalhos),
                String.class);
    }

    @Quando("eu faço transações de {bigdecimal} no cartão {string} com a senha {string} até uma ser recusada, conferindo o saldo após cada uma")
    public void euFacoTransacoesAteUmaSerRecusada(BigDecimal valor, String numero, String senha) {
        BigDecimal saldoEsperado = saldo(numero);
        ultimaResposta = transacao(numero, senha, valor);
        while (ultimaResposta.getStatusCode() == HttpStatus.CREATED) {
            respostasTransacoes.add(ultimaResposta);
            assertThat(ultimaResposta.getBody()).isEqualTo("OK");
            saldoEsperado = saldoEsperado.subtract(valor);
            assertThat(saldo(numero)).isEqualByComparingTo(saldoEsperado);
            ultimaResposta = transacao(numero, senha, valor);
        }
    }

    @Quando("{int} transações de {bigdecimal} são disparadas ao mesmo tempo no cartão {string} com a senha {string}")
    public void transacoesSimultaneas(int quantidade, BigDecimal valor, String numero, String senha) throws Exception {
        List<Callable<ResponseEntity<String>>> tarefas = IntStream.range(0, quantidade)
                .<Callable<ResponseEntity<String>>>mapToObj(i -> () -> transacao(numero, senha, valor))
                .toList();

        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            for (Future<ResponseEntity<String>> futuro : executor.invokeAll(tarefas)) {
                respostasTransacoes.add(futuro.get());
            }
        }
    }

    @Quando("eu consulto o saldo do cartão {string}")
    public void euConsultoOSaldo(String numero) {
        ultimaResposta = autenticado().getForEntity("/cartoes/" + numero, String.class);
    }

    @Quando("eu consulto o saldo do cartão {string} sem credenciais")
    public void euConsultoOSaldoSemCredenciais(String numero) {
        ultimaResposta = restTemplate.getForEntity("/cartoes/" + numero, String.class);
    }

    @Quando("eu acesso {string} sem credenciais")
    public void euAcessoSemCredenciais(String caminho) {
        ultimaResposta = restTemplate.getForEntity(caminho, String.class);
    }

    @Entao("a resposta tem status {int}")
    public void aRespostaTemStatus(int status) {
        assertThat(ultimaResposta.getStatusCode().value()).isEqualTo(status);
    }

    @E("a resposta é uma repetição da original")
    public void aRespostaEUmaRepeticao() {
        assertThat(ultimaResposta.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    }

    @E("o corpo da resposta é {string}")
    public void oCorpoDaRespostaE(String corpo) {
        assertThat(ultimaResposta.getBody()).isEqualTo(corpo);
    }

    @E("o corpo da resposta traz o cartão {string} com a senha {string}")
    public void oCorpoTrazOCartao(String numero, String senha) throws Exception {
        JsonNode corpo = objectMapper.readTree(ultimaResposta.getBody());
        assertThat(corpo.path("numeroCartao").asText()).isEqualTo(numero);
        assertThat(corpo.path("senha").asText()).isEqualTo(senha);
    }

    @E("o detalhe do erro é {string}")
    public void oDetalheDoErroE(String detalhe) throws Exception {
        assertThat(objectMapper.readTree(ultimaResposta.getBody()).path("detail").asText()).isEqualTo(detalhe);
    }

    @E("a resposta não tem corpo")
    public void aRespostaNaoTemCorpo() {
        assertThat(ultimaResposta.getBody()).isNullOrEmpty();
    }

    @E("o saldo do cartão {string} é {bigdecimal}")
    public void oSaldoDoCartaoE(String numero, BigDecimal saldoEsperado) {
        assertThat(saldo(numero)).isEqualByComparingTo(saldoEsperado);
    }

    @Entao("{int} transações foram autorizadas")
    public void transacoesForamAutorizadas(int quantidade) {
        assertThat(respostasTransacoes)
                .filteredOn(resposta -> resposta.getStatusCode() == HttpStatus.CREATED)
                .hasSize(quantidade)
                .allSatisfy(resposta -> assertThat(resposta.getBody()).isEqualTo("OK"));
    }

    @E("{int} transações foram recusadas com {string}")
    public void transacoesForamRecusadas(int quantidade, String motivo) {
        assertThat(respostasTransacoes)
                .filteredOn(resposta -> resposta.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .hasSize(quantidade)
                .allSatisfy(resposta -> assertThat(resposta.getBody()).isEqualTo(motivo));
    }

    private TestRestTemplate autenticado() {
        return restTemplate.withBasicAuth(USUARIO, SENHA_API);
    }

    private ResponseEntity<String> cadastrar(String numero, String senha) {
        return autenticado().postForEntity("/cartoes", Map.of("numeroCartao", numero, "senha", senha), String.class);
    }

    private ResponseEntity<String> transacao(String numero, String senha, BigDecimal valor) {
        return autenticado().postForEntity("/transacoes",
                Map.of("numeroCartao", numero, "senhaCartao", senha, "valor", valor), String.class);
    }

    private BigDecimal saldo(String numero) {
        ResponseEntity<String> resposta = autenticado().getForEntity("/cartoes/" + numero, String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new BigDecimal(resposta.getBody());
    }
}
