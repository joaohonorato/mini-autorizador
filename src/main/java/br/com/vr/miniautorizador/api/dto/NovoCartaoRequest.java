package br.com.vr.miniautorizador.api.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de entrada e de saída do cadastro de cartão, conforme o contrato da API.
 * A ordem dos campos na resposta é a do enunciado ({@code senha}, {@code numeroCartao}).
 */
@JsonPropertyOrder({"senha", "numeroCartao"})
public record NovoCartaoRequest(
        @NotBlank String numeroCartao,
        @NotBlank String senha) {
}
