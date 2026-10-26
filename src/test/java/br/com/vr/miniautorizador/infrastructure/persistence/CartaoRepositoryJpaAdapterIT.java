package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static br.com.vr.miniautorizador.support.ExecucaoParalela.emParalelo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adapter JPA contra o MySQL 5.7 real compartilhado dos testes de integração.
 * É aqui que o update condicional e o tratamento da chave duplicada são verificados: o
 * repositório em memória dos testes de aplicação só reproduz a semântica, não o SQL.
 *
 * <p>Sem a transação de teste do {@code @DataJpaTest}: cada operação precisa ser confirmada
 * no banco para que as threads concorrentes enxerguem umas às outras.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CartaoRepositoryJpaAdapter.class)
@ImportTestcontainers(MySqlTestcontainer.class)
@ExtendWith(OutputCaptureExtension.class)
class CartaoRepositoryJpaAdapterIT {

    private static final NumeroCartao NUMERO = new NumeroCartao("6549873025634501");
    private static final String SENHA = "1234";

    private final CifradorSenhaFake cifrador = new CifradorSenhaFake();

    @Autowired
    private CartaoRepositoryJpaAdapter adapter;

    @Autowired
    private CartaoJpaRepository jpa;

    /**
     * O banco é compartilhado com os cenários Cucumber: limpar antes isola este teste do que
     * eles deixaram, e limpar depois devolve o banco vazio para eles.
     */
    @BeforeEach
    @AfterEach
    void limparTabela() {
        jpa.deleteAll();
    }

    @Test
    @DisplayName("buscar cartão inexistente devolve vazio")
    void buscarInexistente() {
        assertThat(adapter.buscar(NUMERO)).isEmpty();
    }

    @Test
    @DisplayName("salvarNovo persiste número, senha cifrada e saldo, e buscar reconstitui o agregado")
    void salvarNovoEBuscar() {
        Cartao cartao = Cartao.criar(NUMERO, SENHA, cifrador);

        assertThat(adapter.salvarNovo(cartao)).isEqualTo(ResultadoCriacao.CRIADO);

        Cartao lido = adapter.buscar(NUMERO).orElseThrow();
        assertThat(lido.numero()).isEqualTo(NUMERO);
        assertThat(lido.senha()).isEqualTo(cartao.senha());
        assertThat(lido.saldo()).isEqualTo(Dinheiro.de("500.00"));
    }

    @Test
    @DisplayName("salvarNovo de número já cadastrado devolve JA_EXISTENTE e não sobrescreve senha nem saldo")
    void salvarNovoDuplicadoNaoSobrescreve() {
        Cartao original = Cartao.criar(NUMERO, SENHA, cifrador);
        adapter.salvarNovo(original);
        adapter.debitar(NUMERO, Dinheiro.de("100.00"));

        ResultadoCriacao resultado = adapter.salvarNovo(Cartao.criar(NUMERO, "9999", cifrador));

        assertThat(resultado).isEqualTo(ResultadoCriacao.JA_EXISTENTE);
        Cartao lido = adapter.buscar(NUMERO).orElseThrow();
        assertThat(lido.senha()).isEqualTo(original.senha());
        assertThat(lido.saldo()).isEqualTo(Dinheiro.de("400.00"));
    }

    @Test
    @DisplayName("cadastro duplicado não registra o número completo do cartão em log")
    void cadastroDuplicadoNaoVazaNumeroEmLog(CapturedOutput saida) {
        adapter.salvarNovo(Cartao.criar(NUMERO, SENHA, cifrador));
        adapter.salvarNovo(Cartao.criar(NUMERO, "9999", cifrador));

        assertThat(saida.getAll()).doesNotContain(NUMERO.valor());
    }

    @Test
    @DisplayName("debitar com saldo suficiente autoriza e subtrai o valor exato")
    void debitarComSaldo() {
        adapter.salvarNovo(Cartao.criar(NUMERO, SENHA, cifrador));

        assertThat(adapter.debitar(NUMERO, Dinheiro.de("10.01"))).isEqualTo(ResultadoAutorizacao.autorizada());
        assertThat(adapter.buscar(NUMERO).orElseThrow().saldo()).isEqualTo(Dinheiro.de("489.99"));
    }

    @Test
    @DisplayName("debitar o saldo inteiro autoriza e zera o saldo")
    void debitarSaldoInteiro() {
        adapter.salvarNovo(Cartao.criar(NUMERO, SENHA, cifrador));

        assertThat(adapter.debitar(NUMERO, Dinheiro.de("500.00"))).isEqualTo(ResultadoAutorizacao.autorizada());
        assertThat(adapter.buscar(NUMERO).orElseThrow().saldo()).isEqualTo(Dinheiro.de("0.00"));
    }

    @Test
    @DisplayName("debitar acima do saldo persistido recusa sem alterar o saldo")
    void debitarAcimaDoSaldo() {
        adapter.salvarNovo(Cartao.criar(NUMERO, SENHA, cifrador));

        assertThat(adapter.debitar(NUMERO, Dinheiro.de("500.01")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));
        assertThat(adapter.buscar(NUMERO).orElseThrow().saldo()).isEqualTo(Dinheiro.de("500.00"));
    }

    @Test
    @DisplayName("debitar cartão inexistente recusa sem criar linha")
    void debitarCartaoInexistente() {
        assertThat(adapter.debitar(NUMERO, Dinheiro.de("10.00")))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));
        assertThat(jpa.count()).isZero();
    }

    @Test
    @DisplayName("80 débitos simultâneos de 10,00 em 500,00: exatamente 50 autorizados e saldo zerado")
    void debitosSimultaneosNaoDeixamSaldoNegativo() throws Exception {
        adapter.salvarNovo(Cartao.criar(NUMERO, SENHA, cifrador));

        List<ResultadoAutorizacao> resultados = emParalelo(80, () -> adapter.debitar(NUMERO, Dinheiro.de("10.00")));

        assertThat(resultados).filteredOn(ResultadoAutorizacao.autorizada()::equals).hasSize(50);
        assertThat(resultados)
                .filteredOn(ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE)::equals)
                .hasSize(30);
        assertThat(adapter.buscar(NUMERO).orElseThrow().saldo()).isEqualTo(Dinheiro.de("0.00"));
    }

    @Test
    @DisplayName("20 cadastros simultâneos do mesmo número criam um único cartão")
    void cadastrosSimultaneosCriamUmUnicoCartao() throws Exception {
        // Número nunca usado: reaproveitar um recém-apagado faz o InnoDB desfazer inserções por deadlock.
        NumeroCartao numeroNovo = new NumeroCartao("%016d".formatted(ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L)));
        List<Cartao> candidatos = IntStream.range(0, 20)
                .mapToObj(i -> Cartao.criar(numeroNovo, "senha-" + i, cifrador))
                .toList();
        AtomicInteger proximo = new AtomicInteger();

        List<ResultadoCriacao> resultados = emParalelo(20, () -> adapter.salvarNovo(candidatos.get(proximo.getAndIncrement())));

        assertThat(resultados).filteredOn(ResultadoCriacao.CRIADO::equals).hasSize(1);
        assertThat(resultados).filteredOn(ResultadoCriacao.JA_EXISTENTE::equals).hasSize(19);
        assertThat(jpa.count()).isEqualTo(1);
        assertThat(adapter.buscar(numeroNovo).orElseThrow().saldo()).isEqualTo(Dinheiro.de("500.00"));
    }
}
