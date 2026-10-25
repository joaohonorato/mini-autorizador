package br.com.vr.miniautorizador.domain.transacao;

/**
 * Motivos pelos quais uma transação não é autorizada. O nome de cada constante é
 * exatamente o texto devolvido no corpo da resposta da API.
 *
 * <p>Os três primeiros são os do contrato do enunciado. {@link #CARTAO_BLOQUEADO} é uma extensão,
 * só devolvida quando o bloqueio por senhas erradas está ligado (ver {@code PoliticaDeBloqueio}).</p>
 */
public enum MotivoRecusa {
    CARTAO_INEXISTENTE,
    SENHA_INVALIDA,
    SALDO_INSUFICIENTE,
    CARTAO_BLOQUEADO
}
