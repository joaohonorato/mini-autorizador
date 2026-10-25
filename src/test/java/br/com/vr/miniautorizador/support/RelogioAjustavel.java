package br.com.vr.miniautorizador.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relógio parado que os testes avançam explicitamente, para verificar regras que dependem do tempo.
 */
public class RelogioAjustavel extends Clock {

    private Instant agora = Instant.parse("2026-01-15T12:00:00Z");

    public void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return Clock.fixed(agora, zona);
    }
}
