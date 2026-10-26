package br.com.vr.miniautorizador.application;

import br.com.vr.miniautorizador.domain.compartilhado.Dinheiro;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotencia;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia;
import br.com.vr.miniautorizador.domain.transacao.RegistroIdempotencia.Execucao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Caso de uso: autorizar uma transação no máximo uma vez por chave de idempotência.
 *
 * <p>Sem chave, a transação é autorizada como sempre. Com chave, o primeiro envio autoriza e grava o
 * resultado; os reenvios com a mesma chave devolvem o resultado gravado, sem debitar de novo e sem contar
 * outra senha errada. A impressão da requisição cobre o cartão e o valor, não a senha: um resumo da senha
 * guardado no banco permitiria descobrir um PIN de 4 dígitos por tentativa.</p>
 */
public class AutorizarTransacaoIdempotente {

    private static final Logger LOG = LoggerFactory.getLogger(AutorizarTransacaoIdempotente.class);

    private final AutorizarTransacao autorizarTransacao;
    private final RegistroIdempotencia registro;

    public AutorizarTransacaoIdempotente(AutorizarTransacao autorizarTransacao, RegistroIdempotencia registro) {
        this.autorizarTransacao = autorizarTransacao;
        this.registro = registro;
    }

    public Execucao executar(Optional<String> chave, String numeroCartao, String senhaCartao, BigDecimal valor) {
        Dinheiro valorTransacao = Dinheiro.positivo(valor);
        Execucao execucao = chave.map(ChaveIdempotencia::new)
                .map(identificada -> registro.executarUmaVez(identificada, impressao(numeroCartao, valorTransacao),
                        () -> autorizarTransacao.executar(numeroCartao, senhaCartao, valor)))
                .orElseGet(() -> new Execucao.Nova(autorizarTransacao.executar(numeroCartao, senhaCartao, valor)));
        switch (execucao) {
            case Execucao.Nova nova -> LOG.debug("transacao.idempotente.nova");
            case Execucao.Repetida repetida -> LOG.info("transacao.idempotente.repetida");
        }
        return execucao;
    }

    private static String impressao(String numeroCartao, Dinheiro valor) {
        return sha256(numeroCartao + "|" + valor.valor().toPlainString());
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
