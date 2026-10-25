package br.com.vr.miniautorizador.infrastructure.persistence;

import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotencia;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaReutilizadaException;
import br.com.vr.miniautorizador.domain.transacao.MotivoRecusa;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao.Autorizada;
import br.com.vr.miniautorizador.domain.transacao.ResultadoAutorizacao.Recusada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adapter da porta {@link RegistroIdempotencia} com a tabela {@code transacao_idempotente}.
 *
 * <p>Tudo acontece numa transação só: reservar a chave, autorizar (o débito, a contagem de senhas
 * erradas) e gravar o resultado. Os updates do {@code CartaoJpaRepository} entram nessa transação em vez
 * de abrir outra, então o débito e o resultado gravado valem juntos ou não valem. A reserva é um
 * {@code insert ignore}: com a chave nova, grava uma linha; com a chave de outra transação ainda aberta,
 * o InnoDB faz a inserção esperar o commit dela, e então não grava nada. Assim, de duas requisições
 * simultâneas, a segunda só segue depois da primeira e lê o resultado dela.</p>
 *
 * <p>A validade da chave é a da limpeza ({@link LimpezaIdempotencia}): uma chave vencida vale até a
 * limpeza seguinte apagá-la. Conferir a validade a cada requisição exigiria um {@code delete} ou um
 * {@code update} antes do {@code insert}, e requisições simultâneas da mesma chave se travariam em deadlock.</p>
 */
@Component
class RegistroIdempotenciaJdbcAdapter implements RegistroIdempotencia {

    private static final int UMA_LINHA_ALTERADA = 1;
    private static final String CODIGO_AUTORIZADA = "OK";

    private final JdbcClient jdbc;
    private final TransactionTemplate transacao;
    private final Clock relogio;

    RegistroIdempotenciaJdbcAdapter(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    @Override
    public Execucao executarUmaVez(ChaveIdempotencia chave, String impressao, Supplier<ResultadoAutorizacao> acao) {
        return transacao.execute(status -> reservar(chave, impressao)
                .map(reservada -> executar(chave, acao))
                .orElseGet(() -> repetir(chave, impressao)));
    }

    private Optional<Boolean> reservar(ChaveIdempotencia chave, String impressao) {
        int linhas = jdbc.sql("insert ignore into transacao_idempotente (chave, impressao, criada_em) "
                        + "values (:chave, :impressao, :agora)")
                .param("chave", chave.valor())
                .param("impressao", impressao)
                .param("agora", utc(relogio.instant()))
                .update();
        return Optional.of(true).filter(reservada -> linhas == UMA_LINHA_ALTERADA);
    }

    private Execucao executar(ChaveIdempotencia chave, Supplier<ResultadoAutorizacao> acao) {
        ResultadoAutorizacao resultado = acao.get();
        jdbc.sql("update transacao_idempotente set resultado = :resultado where chave = :chave")
                .param("resultado", codigo(resultado))
                .param("chave", chave.valor())
                .update();
        return new Execucao.Nova(resultado);
    }

    /**
     * +Leitura simples, sem {@code for update}: o {@code insert ignore} que não gravou nada já deixou um
     * lock compartilhado na linha, e vários pedindo o lock exclusivo ao mesmo tempo se travariam em
     * deadlock. O instantâneo da transação só nasce nesta leitura, depois do commit da primeira requisição,
     * então ela enxerga o resultado gravado.
     */
    private Execucao repetir(ChaveIdempotencia chave, String impressao) {
        Gravado gravado = jdbc.sql("select impressao, resultado from transacao_idempotente where chave = :chave")
                .param("chave", chave.valor())
                .query((linha, n) -> new Gravado(linha.getString("impressao"), linha.getString("resultado")))
                .single();
        return Optional.of(gravado)
                .filter(g -> g.impressao().equals(impressao))
                .map(g -> (Execucao) new Execucao.Repetida(resultado(g.resultado())))
                .orElseThrow(ChaveIdempotenciaReutilizadaException::new);
    }

    /**
     * Data e hora em UTC sem fuso: o driver grava um {@code LocalDateTime} como está, sem converter pelo
     * fuso da JVM nem pelo da conexão.
     */
    static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static String codigo(ResultadoAutorizacao resultado) {
        return switch (resultado) {
            case Autorizada() -> CODIGO_AUTORIZADA;
            case Recusada(MotivoRecusa motivo) -> motivo.name();
        };
    }

    private static ResultadoAutorizacao resultado(String codigo) {
        return Optional.of(codigo)
                .filter(CODIGO_AUTORIZADA::equals)
                .map(autorizada -> ResultadoAutorizacao.autorizada())
                .orElseGet(() -> ResultadoAutorizacao.recusada(MotivoRecusa.valueOf(codigo)));
    }

    private record Gravado(String impressao, String resultado) {
    }
}
