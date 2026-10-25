package br.com.vr.miniautorizador.support;

import br.com.vr.miniautorizador.domain.cartao.Cartao;
import br.com.vr.miniautorizador.domain.cartao.NumeroCartao;
import br.com.vr.miniautorizador.domain.cartao.ResultadoCriacao;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositório em memória que anota o cartão presente no contexto de log (MDC) a cada acesso, para os
 * testes dos casos de uso provarem que o contexto está preenchido enquanto a operação roda.
 */
public class CartaoRepositoryQueObservaMdc extends CartaoRepositoryEmMemoria {

    private final List<String> cartoesVistos = new ArrayList<>();

    @Override
    public synchronized Optional<Cartao> buscar(NumeroCartao numero) {
        cartoesVistos.add(MDC.get("numeroCartao"));
        return super.buscar(numero);
    }

    @Override
    public synchronized ResultadoCriacao salvarNovo(Cartao cartao) {
        cartoesVistos.add(MDC.get("numeroCartao"));
        return super.salvarNovo(cartao);
    }

    public synchronized List<String> cartoesVistos() {
        return List.copyOf(cartoesVistos);
    }
}
