package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;

import java.util.Optional;

/**
 * Senha do cartão já cifrada. O domínio nunca guarda a senha em texto puro.
 */
public record SenhaCifrada(String hash) {

    public SenhaCifrada {
        hash = Optional.ofNullable(hash)
                .filter(h -> !h.isBlank())
                .orElseThrow(() -> new ValorInvalidoException("Hash de senha não pode ser vazio"));
    }

    @Override
    public String toString() {
        return "SenhaCifrada[****]";
    }
}
