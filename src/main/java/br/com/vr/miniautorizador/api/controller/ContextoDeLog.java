package br.com.vr.miniautorizador.api.controller;

import org.slf4j.MDC;

import java.util.Optional;

/**
 * Coloca o cartão no contexto de log (MDC) só mascarado: os 4 últimos dígitos bastam para rastrear
 * uma transação, e o número completo nunca vai para o log. Quem limpa o contexto ao fim da requisição
 * é o {@code MdcFilter}.
 */
final class ContextoDeLog {

    static final String NUMERO_CARTAO = "numeroCartao";
    private static final int DIGITOS_VISIVEIS = 4;
    private static final String SEM_NUMERO = "****";

    private ContextoDeLog() {
    }

    static String mascarar(String numeroCartao) {
        return Optional.ofNullable(numeroCartao)
                .filter(numero -> numero.length() >= DIGITOS_VISIVEIS)
                .map(numero -> "*".repeat(numero.length() - DIGITOS_VISIVEIS)
                        + numero.substring(numero.length() - DIGITOS_VISIVEIS))
                .orElse(SEM_NUMERO);
    }

    static void porCartao(String numeroCartao) {
        MDC.put(NUMERO_CARTAO, mascarar(numeroCartao));
    }
}
