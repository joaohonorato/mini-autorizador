package br.com.vr.miniautorizador.domain.cartao;

import java.time.Duration;
import java.time.Instant;

/**
 * Regra de bloqueio do cartão por senhas erradas. A senha é um PIN de 4 dígitos, então sem
 * limite qualquer pessoa descobriria a senha em até 10 mil tentativas.
 *
 * <p>Só as senhas erradas contam, e cada erro é somado por um update atômico no repositório: o
 * erro que completa o limite bloqueia o cartão pela duração abaixo, e a senha certa zera a
 * contagem. Tentativas em sequência param no limite. Uma rajada de tentativas simultâneas pode
 * conferir mais senhas antes de o bloqueio valer, até o número de requisições que o servidor
 * atende ao mesmo tempo; contar a tentativa antes de conferir a senha evitaria isso, mas também
 * barraria transações legítimas simultâneas do mesmo cartão.</p>
 */
public final class PoliticaDeBloqueio {

    public static final int TENTATIVAS_DE_SENHA = 3;
    public static final Duration DURACAO_DO_BLOQUEIO = Duration.ofHours(24);

    private PoliticaDeBloqueio() {
    }

    public static Instant bloqueioAte(Instant agora) {
        return agora.plus(DURACAO_DO_BLOQUEIO);
    }
}
