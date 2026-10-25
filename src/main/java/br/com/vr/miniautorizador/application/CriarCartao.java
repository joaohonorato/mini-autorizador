package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.CartaoRepository;
import br.com.vr.miniautorizador.domain.cartao.CifradorSenha;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso: cadastrar um cartão novo com o saldo inicial.
 */
public class CriarCartao {

    private static final Logger LOG = LoggerFactory.getLogger(CriarCartao.class);

    private final CartaoRepository repositorio;
    private final CifradorSenha cifrador;

    public CriarCartao(CartaoRepository repositorio, CifradorSenha cifrador) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
    }

    public ResultadoCriacao executar(String numeroCartao, String senha) {
        MdcContexto.porCartao(numeroCartao);
        try {
            Cartao cartao = Cartao.criar(new NumeroCartao(numeroCartao), senha, cifrador);
            ResultadoCriacao resultado = repositorio.salvarNovo(cartao);
            LOG.info("cartao.{}", descricao(resultado));
            return resultado;
        } finally {
            MdcContexto.limpar();
        }
    }

    private static String descricao(ResultadoCriacao resultado) {
        return switch (resultado) {
            case CRIADO -> "criado";
            case JA_EXISTENTE -> "duplicado";
        };
    }
}
