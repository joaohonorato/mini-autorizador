package br.com.vr.miniautorizador.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Número e senha só são obrigatórios: se vierem em branco, chegam às regras de autorização e
 * são recusados como CARTAO_INEXISTENTE ou SENHA_INVALIDA, como qualquer outro valor que não
 * corresponda a um cartão cadastrado.
 */
public record TransacaoRequest(
        @NotNull String numeroCartao,
        @NotNull String senhaCartao,
        @NotNull @Positive BigDecimal valor) {
}
