package br.com.vr.miniautorizador.domain.cartao;

/**
 * Resultado da tentativa de cadastrar um cartão. Cartão já existente é um desfecho
 * esperado do negócio, não uma falha, por isso é um valor e não uma exceção.
 */
public enum ResultadoCriacao {
    CRIADO,
    JA_EXISTENTE
}
