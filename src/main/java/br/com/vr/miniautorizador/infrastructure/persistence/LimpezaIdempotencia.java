package br.com.vr.miniautorizador.infrastructure.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Apaga as chaves de idempotência vencidas, para a tabela não crescer sem limite. Cada instância
 * roda a limpeza; o {@code delete} é idempotente, então rodar em mais de uma não causa problema.
 */
@Component
class LimpezaIdempotencia {

    private final JdbcClient jdbc;
    private final Clock relogio;
    private final Duration validade;

    LimpezaIdempotencia(JdbcClient jdbc, Clock relogio,
                        @Value("${autorizador.idempotencia.validade}") Duration validade) {
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.validade = validade;
    }

    @Scheduled(fixedDelayString = "${autorizador.idempotencia.intervalo-limpeza}")
    void apagarVencidas() {
        jdbc.sql("delete from transacao_idempotente where criada_em < :limite")
                .param("limite", RegistroIdempotenciaJdbcAdapter.utc(relogio.instant().minus(validade)))
                .update();
    }
}
