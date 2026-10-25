package br.com.vr.miniautorizador.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas, como a limpeza das chaves de idempotência vencidas.
 */
@Configuration
@EnableScheduling
class AgendamentoConfig {
}
