package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.PoliticaDeBloqueio;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.IntStream;

import static br.com.vr.miniautorizador.support.ExecucaoParalela.emParalelo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloqueio por senhas erradas contra o MySQL 5.7 real. O adapter só existe com o bloqueio ligado,
 * por isso a propriedade é definida aqui. Sem a transação de teste, cada update é confirmado no
 * banco, como em produção.
 */
@DataJpaTest(properties = "autorizador.bloqueio-senha.ativo=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CartaoRepositoryJpaAdapter.class, BloqueioPorSenhaJpaAdapter.class})
@ImportTestcontainers(MySqlTestcontainer.class)
class BloqueioPorSenhaJpaAdapterIT {

    private static final NumeroCartao NUMERO = new NumeroCartao("6549873025634501");
    private static final Instant AGORA = Instant.parse("2026-01-15T12:00:00.123456Z");

    private final CifradorSenhaFake cifrador = new CifradorSenhaFake();

    @Autowired
    private CartaoRepositoryJpaAdapter cartoes;

    @Autowired
    private BloqueioPorSenhaJpaAdapter bloqueio;

    @Autowired
    private CartaoJpaRepository jpa;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * O banco é compartilhado com os demais testes de integração, que também usam este número.
     */
    @BeforeEach
    @AfterEach
    void limparTabela() {
        jpa.deleteAll();
    }

    @BeforeEach
    void cadastrarCartao() {
        cartoes.salvarNovo(Cartao.criar(NUMERO, "1234", cifrador));
    }

    @Test
    @DisplayName("2 senhas erradas não bloqueiam o cartão; a 3ª bloqueia")
    void terceiraSenhaErradaBloqueia() {
        bloqueio.registrarSenhaErrada(NUMERO, AGORA);
        bloqueio.registrarSenhaErrada(NUMERO, AGORA);
        assertThat(bloqueio.verificarDesbloqueado(NUMERO, AGORA)).isEqualTo(ResultadoAutorizacao.autorizada());

        bloqueio.registrarSenhaErrada(NUMERO, AGORA);
        assertThat(bloqueio.verificarDesbloqueado(NUMERO, AGORA))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));
    }

    @Test
    @DisplayName("a senha certa zera a contagem de senhas erradas")
    void senhaCertaZeraContagem() {
        bloqueio.registrarSenhaErrada(NUMERO, AGORA);
        bloqueio.registrarSenhaErrada(NUMERO, AGORA);

        assertThat(bloqueio.registrarSenhaCerta(NUMERO, AGORA)).isEqualTo(ResultadoAutorizacao.autorizada());

        bloqueio.registrarSenhaErrada(NUMERO, AGORA);
        bloqueio.registrarSenhaErrada(NUMERO, AGORA);
        assertThat(bloqueio.verificarDesbloqueado(NUMERO, AGORA))
                .as("2 erradas desde a senha certa: ainda não bloqueado")
                .isEqualTo(ResultadoAutorizacao.autorizada());
    }

    @Test
    @DisplayName("senha certa com o cartão já bloqueado é recusada com CARTAO_BLOQUEADO")
    void senhaCertaComCartaoBloqueado() {
        IntStream.range(0, 3).forEach(i -> bloqueio.registrarSenhaErrada(NUMERO, AGORA));

        assertThat(bloqueio.registrarSenhaCerta(NUMERO, AGORA))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));
    }

    @Test
    @DisplayName("o bloqueio vale por 24 horas e depois a contagem recomeça do zero")
    void bloqueioVence() {
        IntStream.range(0, 3).forEach(i -> bloqueio.registrarSenhaErrada(NUMERO, AGORA));
        Instant fimDoBloqueio = AGORA.plus(PoliticaDeBloqueio.DURACAO_DO_BLOQUEIO);

        assertThat(bloqueio.verificarDesbloqueado(NUMERO, fimDoBloqueio.minusMillis(1)))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));
        assertThat(bloqueio.verificarDesbloqueado(NUMERO, fimDoBloqueio)).isEqualTo(ResultadoAutorizacao.autorizada());

        bloqueio.registrarSenhaErrada(NUMERO, fimDoBloqueio);
        bloqueio.registrarSenhaErrada(NUMERO, fimDoBloqueio);
        assertThat(bloqueio.verificarDesbloqueado(NUMERO, fimDoBloqueio))
                .as("a contagem recomeçou: 2 erradas depois do bloqueio")
                .isEqualTo(ResultadoAutorizacao.autorizada());

        bloqueio.registrarSenhaErrada(NUMERO, fimDoBloqueio);
        assertThat(bloqueio.verificarDesbloqueado(NUMERO, fimDoBloqueio))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));
    }

    @Test
    @DisplayName("50 senhas erradas simultâneas bloqueiam o cartão e a contagem para exatamente no limite")
    void senhasErradasSimultaneasContamAteOLimite() throws Exception {
        emParalelo(50, () -> {
            bloqueio.registrarSenhaErrada(NUMERO, AGORA);
            return null;
        });

        assertThat(bloqueio.verificarDesbloqueado(NUMERO, AGORA))
                .isEqualTo(ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO));
        assertThat(jdbc.queryForObject(
                "select tentativas_senha from cartao where numero = ?", Integer.class, NUMERO.valor()))
                .isEqualTo(PoliticaDeBloqueio.TENTATIVAS_DE_SENHA);
    }
}
