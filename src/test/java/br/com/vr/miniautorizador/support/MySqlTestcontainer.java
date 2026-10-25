package br.com.vr.miniautorizador.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL 5.7 compartilhado pelos testes de integração, a mesma versão do docker-compose do
 * enunciado. Importado com {@code @ImportTestcontainers}: por ser estático, é iniciado uma
 * única vez por JVM e reaproveitado pelos contextos do adapter JPA e do Cucumber.
 */
public final class MySqlTestcontainer {

    @ServiceConnection
    public static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7");

    private MySqlTestcontainer() {
    }
}
