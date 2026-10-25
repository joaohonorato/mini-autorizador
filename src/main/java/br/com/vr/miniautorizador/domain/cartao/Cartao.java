package br.com.vr.miniautorizador.domain.cartao;

import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;

import java.util.Objects;
import java.util.Optional;

/**
 * Agregado raiz. Concentra as regras de autorização que dependem apenas do estado do cartão.
 *
 * <p>O cartão decide se uma transação pode ser autorizada; o débito em si é aplicado de forma
 * atômica pelo {@link CartaoRepository}, que revalida o saldo no banco para proteger o
 * invariante "saldo nunca negativo" contra transações concorrentes.</p>
 */
public final class Cartao {

    public static final Dinheiro SALDO_INICIAL = Dinheiro.de("500.00");

    private final NumeroCartao numero;
    private final SenhaCifrada senha;
    private final Dinheiro saldo;

    private Cartao(NumeroCartao numero, SenhaCifrada senha, Dinheiro saldo) {
        this.numero = Objects.requireNonNull(numero, "numero");
        this.senha = Objects.requireNonNull(senha, "senha");
        this.saldo = Objects.requireNonNull(saldo, "saldo");
    }

    /**
     * Fábrica para um cartão novo: aplica a regra de negócio do saldo inicial.
     */
    public static Cartao criar(NumeroCartao numero, String senhaAberta, CifradorSenha cifrador) {
        String senhaValida = Optional.ofNullable(senhaAberta)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new ValorInvalidoException("Senha do cartão não pode ser vazia"));
        return new Cartao(numero, cifrador.cifrar(senhaValida), SALDO_INICIAL);
    }

    /**
     * Fábrica para reconstruir um cartão já existente a partir da persistência.
     * Não reaplica regras de criação, como o saldo inicial.
     */
    public static Cartao reconstituir(NumeroCartao numero, SenhaCifrada senha, Dinheiro saldo) {
        return new Cartao(numero, senha, saldo);
    }

    /**
     * Regra de autorização: a senha informada é a do cartão.
     */
    public ResultadoAutorizacao conferirSenha(String senhaInformada, CifradorSenha cifrador) {
        boolean senhaConfere = Optional.ofNullable(senhaInformada)
                .filter(s -> cifrador.confere(s, senha))
                .isPresent();
        return ResultadoAutorizacao.exigir(senhaConfere, MotivoRecusa.SENHA_INVALIDA);
    }

    /**
     * Regra de autorização: o saldo lido cobre o valor. É uma checagem prévia; a garantia contra
     * débitos concorrentes é o débito condicional do {@link CartaoRepository}.
     */
    public ResultadoAutorizacao verificarSaldo(Dinheiro valor) {
        return ResultadoAutorizacao.exigir(saldo.cobre(valor), MotivoRecusa.SALDO_INSUFICIENTE);
    }

    public NumeroCartao numero() {
        return numero;
    }

    public SenhaCifrada senha() {
        return senha;
    }

    public Dinheiro saldo() {
        return saldo;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Cartao cartao && numero.equals(cartao.numero);
    }

    @Override
    public int hashCode() {
        return numero.hashCode();
    }

    @Override
    public String toString() {
        return "Cartao[numero=" + numero + ", saldo=" + saldo.valor() + "]";
    }
}
