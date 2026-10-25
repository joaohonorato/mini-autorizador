package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotencia;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaReutilizadaException;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia.Execucao;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.vr.miniautorizador.support.ExecucaoParalela.emParalelo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O registro de idempotência contra o MySQL 5.7 real. Sem a transação de teste, cada execução é
 * confirmada no banco, como em produção; é isso que faz a segunda requisição esperar pela primeira.
 */
@JdbcTest(properties = {"autorizador.idempotencia.validade=PT24H", "autorizador.idempotencia.intervalo-limpeza=PT1H"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RegistroIdempotenciaJdbcAdapter.class, LimpezaIdempotencia.class, RegistroIdempotenciaJdbcAdapterIT.Relogio.class})
@ImportTestcontainers(MySqlTestcontainer.class)
class RegistroIdempotenciaJdbcAdapterIT {

    static class Relogio {
        @Bean
        Clock relogio() {
            return Clock.systemUTC();
        }
    }

    private static final String IMPRESSAO = "a".repeat(64);
    private static final ResultadoAutorizacao AUTORIZADA = ResultadoAutorizacao.autorizada();

    @Autowired
    private RegistroIdempotenciaJdbcAdapter registro;

    @Autowired
    private LimpezaIdempotencia limpeza;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limparChaves() {
        jdbc.update("delete from transacao_idempotente");
    }

    @Test
    @DisplayName("a primeira execução roda a ação e grava o resultado; a segunda repete sem rodar a ação")
    void primeiraExecutaESegundaRepete() {
        AtomicInteger execucoes = new AtomicInteger();
        ChaveIdempotencia chave = new ChaveIdempotencia("it-repete");

        Execucao primeira = registro.executarUmaVez(chave, IMPRESSAO, () -> {
            execucoes.incrementAndGet();
            return AUTORIZADA;
        });
        Execucao segunda = registro.executarUmaVez(chave, IMPRESSAO, () -> {
            execucoes.incrementAndGet();
            return AUTORIZADA;
        });

        assertThat(primeira).isEqualTo(new Execucao.Nova(AUTORIZADA));
        assertThat(segunda).isEqualTo(new Execucao.Repetida(AUTORIZADA));
        assertThat(execucoes).hasValue(1);
    }

    @Test
    @DisplayName("cada motivo de recusa é gravado e devolvido igual")
    void recusasSaoRepetidasIguais() {
        for (MotivoRecusa motivo : MotivoRecusa.values()) {
            ChaveIdempotencia chave = new ChaveIdempotencia("it-recusa-" + motivo);
            ResultadoAutorizacao recusa = ResultadoAutorizacao.recusada(motivo);

            registro.executarUmaVez(chave, IMPRESSAO, () -> recusa);

            assertThat(registro.executarUmaVez(chave, IMPRESSAO, () -> AUTORIZADA))
                    .isEqualTo(new Execucao.Repetida(recusa));
        }
    }

    @Test
    @DisplayName("a mesma chave com outra impressão é recusada e a ação não roda")
    void chaveComOutraImpressao() {
        ChaveIdempotencia chave = new ChaveIdempotencia("it-diverge");
        registro.executarUmaVez(chave, IMPRESSAO, () -> AUTORIZADA);
        AtomicInteger execucoes = new AtomicInteger();

        assertThatThrownBy(() -> registro.executarUmaVez(chave, "b".repeat(64), () -> {
            execucoes.incrementAndGet();
            return AUTORIZADA;
        })).isInstanceOf(ChaveIdempotenciaReutilizadaException.class);
        assertThat(execucoes).hasValue(0);
    }

    @Test
    @DisplayName("se a ação falha, nada é gravado e a chave continua livre")
    void acaoQueFalhaLiberaAChave() {
        ChaveIdempotencia chave = new ChaveIdempotencia("it-falha");

        assertThatThrownBy(() -> registro.executarUmaVez(chave, IMPRESSAO, () -> {
            throw new IllegalStateException("falha da ação");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select count(*) from transacao_idempotente", Integer.class)).isZero();
        assertThat(registro.executarUmaVez(chave, IMPRESSAO, () -> AUTORIZADA))
                .isEqualTo(new Execucao.Nova(AUTORIZADA));
    }

    @Test
    @DisplayName("30 requisições simultâneas com a mesma chave rodam a ação uma vez e as outras repetem o resultado")
    void simultaneasRodamUmaVez() throws Exception {
        AtomicInteger execucoes = new AtomicInteger();
        ChaveIdempotencia chave = new ChaveIdempotencia("it-corrida");

        List<Execucao> execucoesDevolvidas = emParalelo(30, () -> registro.executarUmaVez(chave, IMPRESSAO, () -> {
            execucoes.incrementAndGet();
            return AUTORIZADA;
        }));

        assertThat(execucoes).hasValue(1);
        assertThat(execucoesDevolvidas).filteredOn(Execucao.Nova.class::isInstance).hasSize(1);
        assertThat(execucoesDevolvidas).filteredOn(Execucao.Repetida.class::isInstance).hasSize(29);
    }

    @Test
    @DisplayName("a limpeza apaga só as chaves vencidas")
    void limpezaApagaSoAsVencidas() {
        registro.executarUmaVez(new ChaveIdempotencia("it-velha"), IMPRESSAO, () -> AUTORIZADA);
        registro.executarUmaVez(new ChaveIdempotencia("it-nova"), IMPRESSAO, () -> AUTORIZADA);
        jdbc.update("update transacao_idempotente set criada_em = utc_timestamp(6) - interval 25 hour where chave = 'it-velha'");

        limpeza.apagarVencidas();

        assertThat(jdbc.queryForList("select chave from transacao_idempotente", String.class)).containsExactly("it-nova");
    }
}
