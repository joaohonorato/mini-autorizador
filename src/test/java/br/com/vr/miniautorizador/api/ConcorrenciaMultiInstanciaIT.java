package br.com.vr.miniautorizador.api;

import br.com.vr.miniautorizador.MiniAutorizadorApplication;
import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A garantia de saldo está no banco, então só vale como prova se duas instâncias da aplicação, com
 * memória separada, disputarem o mesmo cartão no mesmo MySQL. Cada instância é um contexto Spring
 * completo, com o próprio servidor HTTP e o próprio pool de conexões, como duas réplicas em produção.
 */
class ConcorrenciaMultiInstanciaIT {

    private static final String NUMERO = "5200000000000001";
    private static final int TRANSACOES = 100;
    private static final BigDecimal VALOR = new BigDecimal("10.00");

    private static ConfigurableApplicationContext instanciaA;
    private static ConfigurableApplicationContext instanciaB;

    @BeforeAll
    static void subirDuasInstancias() {
        MySqlTestcontainer.MYSQL.start();
        instanciaA = subirInstancia();
        instanciaB = subirInstancia();
    }

    @AfterAll
    static void derrubarInstancias() {
        instanciaB.close();
        instanciaA.close();
    }

    @Test
    @DisplayName("100 transações de R$ 10,00 divididas entre duas instâncias aprovam exatamente 50 e zeram o saldo")
    void duasInstanciasNaoDebitamAlemDoSaldo() throws Exception {
        TestRestTemplate a = cliente(instanciaA);
        TestRestTemplate b = cliente(instanciaB);
        assertThat(a.postForEntity("/cartoes", Map.of("numeroCartao", NUMERO, "senha", "1234"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Callable<ResponseEntity<String>>> tarefas = IntStream.range(0, TRANSACOES)
                .<Callable<ResponseEntity<String>>>mapToObj(i -> () -> transacao(i % 2 == 0 ? a : b))
                .toList();
        List<ResponseEntity<String>> respostas;
        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            respostas = executor.invokeAll(tarefas).stream().map(ConcorrenciaMultiInstanciaIT::resultado).toList();
        }

        assertThat(respostas).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED)
                .hasSize(50).allSatisfy(r -> assertThat(r.getBody()).isEqualTo("OK"));
        assertThat(respostas).filteredOn(r -> r.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .hasSize(50).allSatisfy(r -> assertThat(r.getBody()).isEqualTo("SALDO_INSUFICIENTE"));
        assertThat(new BigDecimal(a.getForObject("/cartoes/" + NUMERO, String.class))).isEqualByComparingTo("0.00");
        assertThat(new BigDecimal(b.getForObject("/cartoes/" + NUMERO, String.class))).isEqualByComparingTo("0.00");
    }

    private static ConfigurableApplicationContext subirInstancia() {
        return new SpringApplicationBuilder(MiniAutorizadorApplication.class)
                .properties("server.port=0")
                .run(
                        "--spring.datasource.url=" + MySqlTestcontainer.MYSQL.getJdbcUrl(),
                        "--spring.datasource.username=" + MySqlTestcontainer.MYSQL.getUsername(),
                        "--spring.datasource.password=" + MySqlTestcontainer.MYSQL.getPassword());
    }

    private static TestRestTemplate cliente(ConfigurableApplicationContext instancia) {
        String porta = instancia.getEnvironment().getProperty("local.server.port");
        return new TestRestTemplate(new RestTemplateBuilder().rootUri("http://localhost:" + porta), "username", "password");
    }

    private static ResponseEntity<String> transacao(TestRestTemplate instancia) {
        return instancia.postForEntity("/transacoes",
                Map.of("numeroCartao", NUMERO, "senhaCartao", "1234", "valor", VALOR), String.class);
    }

    private static ResponseEntity<String> resultado(Future<ResponseEntity<String>> futuro) {
        try {
            return futuro.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
