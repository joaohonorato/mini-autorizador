package br.com.vr.miniautorizador.infrastructure.seguranca;

import br.com.vr.miniautorizador.domain.cartao.CifradorSenha;
import br.com.vr.miniautorizador.domain.cartao.SenhaCifrada;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Adapter de {@link CifradorSenha} com BCrypt: hash com salt e custo configurável,
 * cuja verificação não vaza informação por diferença de tempo.
 *
 * <p>O BCrypt só considera os primeiros 72 bytes da senha. Sem limite, uma senha cadastrada
 * com mais bytes que isso aceitaria qualquer tentativa com os mesmos 72 primeiros. Por isso o
 * cadastro rejeita senhas maiores, e uma tentativa maior nunca confere.</p>
 */
@Component
class BCryptCifradorSenha implements CifradorSenha {

    private static final int LIMITE_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public SenhaCifrada cifrar(String senhaAberta) {
        String senha = Optional.of(senhaAberta)
                .filter(BCryptCifradorSenha::cabeNoLimite)
                .orElseThrow(() -> new ValorInvalidoException(
                        "Senha do cartão deve ter no máximo " + LIMITE_BYTES + " bytes"));
        return new SenhaCifrada(encoder.encode(senha));
    }

    @Override
    public boolean confere(String senhaAberta, SenhaCifrada senhaCifrada) {
        return cabeNoLimite(senhaAberta) && encoder.matches(senhaAberta, senhaCifrada.hash());
    }

    private static boolean cabeNoLimite(String senha) {
        return senha.getBytes(StandardCharsets.UTF_8).length <= LIMITE_BYTES;
    }
}
