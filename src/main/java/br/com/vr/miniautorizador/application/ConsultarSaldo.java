package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.CartaoRepository;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Caso de uso: consultar o saldo disponível de um cartão.
 */
public class ConsultarSaldo {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultarSaldo.class);

    private final CartaoRepository repositorio;

    public ConsultarSaldo(CartaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * @return o saldo, ou vazio se não existir cartão com esse número
     */
    public Optional<Dinheiro> executar(String numeroCartao) {
        MdcContexto.porCartao(numeroCartao);
        try {
            Optional<Dinheiro> saldo = NumeroCartao.tentar(numeroCartao)
                    .flatMap(repositorio::buscar)
                    .map(Cartao::saldo);
            saldo.ifPresentOrElse(
                    encontrado -> LOG.debug("saldo.consultado saldo={}", encontrado.valor()),
                    () -> LOG.info("cartao.nao.encontrado"));
            return saldo;
        } finally {
            MdcContexto.limpar();
        }
    }
}
