package br.com.vr.miniautorizador.infrastructure.config;

import br.com.vr.miniautorizador.application.AutorizarTransacao;
import br.com.vr.miniautorizador.application.AutorizarTransacaoIdempotente;
import br.com.vr.miniautorizador.application.ConsultarSaldo;
import br.com.vr.miniautorizador.application.CriarCartao;
import br.com.vr.miniautorizador.domain.cartao.BloqueioPorSenha;
import br.com.vr.miniautorizador.domain.cartao.CartaoRepository;
import br.com.vr.miniautorizador.domain.cartao.CifradorSenha;
import br.com.vr.miniautorizador.domain.cartao.SemBloqueioPorSenha;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Registra os casos de uso como beans. Assim as camadas de domínio e aplicação
 * ficam livres de anotações do Spring.
 */
@Configuration
class CasosDeUsoConfig {

    @Bean
    CriarCartao criarCartao(CartaoRepository repositorio, CifradorSenha cifrador) {
        return new CriarCartao(repositorio, cifrador);
    }

    @Bean
    ConsultarSaldo consultarSaldo(CartaoRepository repositorio) {
        return new ConsultarSaldo(repositorio);
    }

    @Bean
    AutorizarTransacao autorizarTransacao(CartaoRepository repositorio, BloqueioPorSenha bloqueio,
                                          CifradorSenha cifrador, Clock relogio) {
        return new AutorizarTransacao(repositorio, bloqueio, cifrador, relogio);
    }

    @Bean
    AutorizarTransacaoIdempotente autorizarTransacaoIdempotente(AutorizarTransacao autorizarTransacao,
                                                                RegistroIdempotencia registro) {
        return new AutorizarTransacaoIdempotente(autorizarTransacao, registro);
    }

    /**
     * Bloqueio por senhas erradas desligado, que é o padrão. Ligado
     * ({@code autorizador.bloqueio-senha.ativo=true}), o bean é o {@code BloqueioPorSenhaJpaAdapter}.
     */
    @Bean
    @ConditionalOnProperty(name = "autorizador.bloqueio-senha.ativo", havingValue = "false", matchIfMissing = true)
    BloqueioPorSenha semBloqueioPorSenha() {
        return new SemBloqueioPorSenha();
    }

    /**
     * Relógio usado nas regras que dependem do tempo, como o fim do bloqueio por senhas erradas.
     */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
