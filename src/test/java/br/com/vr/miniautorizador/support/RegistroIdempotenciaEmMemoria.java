package br.com.vr.miniautorizador.support;

import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotencia;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaReutilizadaException;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implementação em memória da porta, com a mesma semântica do adapter JDBC: a primeira execução
 * grava o resultado, as seguintes o repetem, e uma chave com outra impressão é recusada. O
 * {@code synchronized} faz o papel da espera do InnoDB entre requisições simultâneas.
 */
public class RegistroIdempotenciaEmMemoria implements RegistroIdempotencia {

    private record Gravado(String impressao, ResultadoAutorizacao resultado) {
    }

    private final Map<ChaveIdempotencia, Gravado> gravados = new HashMap<>();

    @Override
    public synchronized Execucao executarUmaVez(ChaveIdempotencia chave, String impressao,
                                                Supplier<ResultadoAutorizacao> acao) {
        return Optional.ofNullable(gravados.get(chave))
                .map(gravado -> repetir(gravado, impressao))
                .orElseGet(() -> executar(chave, impressao, acao));
    }

    private Execucao executar(ChaveIdempotencia chave, String impressao, Supplier<ResultadoAutorizacao> acao) {
        ResultadoAutorizacao resultado = acao.get();
        gravados.put(chave, new Gravado(impressao, resultado));
        return new Execucao.Nova(resultado);
    }

    private static Execucao repetir(Gravado gravado, String impressao) {
        return Optional.of(gravado)
                .filter(g -> g.impressao().equals(impressao))
                .<Execucao>map(g -> new Execucao.Repetida(g.resultado()))
                .orElseThrow(ChaveIdempotenciaReutilizadaException::new);
    }

    public synchronized int quantidade() {
        return gravados.size();
    }
}
