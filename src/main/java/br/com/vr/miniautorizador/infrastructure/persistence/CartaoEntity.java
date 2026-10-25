package br.com.vr.miniautorizador.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Modelo de persistência do cartão. Separado do agregado de domínio para que as exigências
 * do JPA (construtor vazio, campos mutáveis, proxies) não contaminem o modelo de negócio.
 *
 * <p>Implementa {@link Persistable} porque o ID é atribuído pela aplicação: sem isso, o
 * {@code save} faria um merge e sobrescreveria um cartão já existente em vez de falhar.</p>
 */
@Entity
@Table(name = "cartao")
public class CartaoEntity implements Persistable<String> {

    @Id
    @Column(name = "numero", length = 19)
    private String numero;

    @Column(name = "senha", nullable = false, length = 100)
    private String senha;

    @Column(name = "saldo", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;

    /**
     * Estado do bloqueio por senhas erradas. Só é lido e alterado pelos updates atômicos de
     * {@link CartaoJpaRepository}, por isso não tem getters nem entra no agregado.
     */
    @Column(name = "tentativas_senha", nullable = false)
    private int tentativasSenha;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    @Transient
    private boolean novo = true;

    protected CartaoEntity() {
        // exigido pelo JPA
    }

    CartaoEntity(String numero, String senha, BigDecimal saldo) {
        this.numero = numero;
        this.senha = senha;
        this.saldo = saldo;
    }

    @PostLoad
    @PostPersist
    void marcarComoPersistido() {
        this.novo = false;
    }

    @Override
    public String getId() {
        return numero;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    String getNumero() {
        return numero;
    }

    String getSenha() {
        return senha;
    }

    BigDecimal getSaldo() {
        return saldo;
    }
}
