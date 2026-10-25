package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import br.com.vr.miniautorizador.support.CifradorSenhaFake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Política de nova tentativa do cadastro quando o MySQL desfaz a transação por deadlock.
 * O deadlock real é intermitente (ver CartaoRepositoryJpaAdapterIT); aqui ele é simulado para
 * verificar, de forma determinística, quantas vezes a inserção é tentada.
 */
class CartaoRepositoryJpaAdapterTest {

    private static final CannotAcquireLockException DEADLOCK =
            new CannotAcquireLockException("Deadlock found when trying to get lock");

    private final CartaoJpaRepository jpa = mock(CartaoJpaRepository.class);
    private final CartaoRepositoryJpaAdapter adapter = new CartaoRepositoryJpaAdapter(jpa);
    private final Cartao cartao = Cartao.criar(new NumeroCartao("6549873025634501"), "1234", new CifradorSenhaFake());

    @Test
    @DisplayName("deadlock na primeira tentativa repete a inserção, que cria o cartão")
    void deadlockSeguidoDeSucesso() {
        when(jpa.saveAndFlush(any())).thenThrow(DEADLOCK).thenAnswer(invocacao -> invocacao.getArgument(0));

        assertThat(adapter.salvarNovo(cartao)).isEqualTo(ResultadoCriacao.CRIADO);
        verify(jpa, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("deadlock seguido de chave duplicada resulta em cartão já existente")
    void deadlockSeguidoDeChaveDuplicada() {
        when(jpa.saveAndFlush(any())).thenThrow(DEADLOCK).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThat(adapter.salvarNovo(cartao)).isEqualTo(ResultadoCriacao.JA_EXISTENTE);
        verify(jpa, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("deadlock persistente desiste depois de 3 tentativas e propaga o erro")
    void deadlockPersistente() {
        when(jpa.saveAndFlush(any())).thenThrow(DEADLOCK);

        assertThatThrownBy(() -> adapter.salvarNovo(cartao)).isSameAs(DEADLOCK);
        verify(jpa, times(3)).saveAndFlush(any());
    }
}
