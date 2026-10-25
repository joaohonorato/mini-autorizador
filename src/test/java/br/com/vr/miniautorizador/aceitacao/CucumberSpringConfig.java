package br.com.vr.miniautorizador.aceitacao;

import br.com.vr.miniautorizador.support.MySqlTestcontainer;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * Sobe a aplicação inteira em porta aleatória contra o MySQL 5.7 compartilhado dos testes
 * de integração. O contexto é compartilhado por todos os cenários.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestcontainer.class)
public class CucumberSpringConfig {
}
