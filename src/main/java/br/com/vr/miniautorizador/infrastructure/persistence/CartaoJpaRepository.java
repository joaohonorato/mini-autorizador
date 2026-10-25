package br.com.vr.miniautorizador.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

interface CartaoJpaRepository extends JpaRepository<CartaoEntity, String> {

    /**
     * Update condicional atômico: o banco só debita se o saldo atual cobrir o valor.
     * Como é uma única instrução, duas instâncias da aplicação não conseguem debitar
     * o mesmo saldo duas vezes.
     *
     * @return quantidade de linhas alteradas: 1 se debitou, 0 se o saldo não cobria o valor
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CartaoEntity c
               set c.saldo = c.saldo - :valor
             where c.numero = :numero
               and c.saldo >= :valor
            """)
    int debitarSeHouverSaldo(@Param("numero") String numero, @Param("valor") BigDecimal valor);

    /**
     * @return 1 se o cartão existe e não está bloqueado em {@code agora}; 0 caso contrário
     */
    @Query("""
            select count(c) from CartaoEntity c
             where c.numero = :numero
               and (c.bloqueadoAte is null or c.bloqueadoAte <= :agora)
            """)
    long contarDesbloqueado(@Param("numero") String numero, @Param("agora") Instant agora);

    /**
     * Encerra um bloqueio já vencido, para que a contagem de senhas erradas recomece do zero.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CartaoEntity c
               set c.tentativasSenha = 0,
                   c.bloqueadoAte = null
             where c.numero = :numero
               and c.bloqueadoAte <= :agora
            """)
    int encerrarBloqueioVencido(@Param("numero") String numero, @Param("agora") Instant agora);

    /**
     * Soma uma senha errada se o cartão não estiver bloqueado. Como o débito, é uma única
     * instrução: erros simultâneos, mesmo de instâncias diferentes, são contados um a um, e o que
     * completa o limite já grava o fim do bloqueio. Depois disso o {@code where} não casa mais, e a
     * contagem para no limite.
     *
     * <p>{@code bloqueadoAte} é atribuído antes de {@code tentativasSenha}: o MySQL avalia as
     * atribuições da esquerda para a direita, e assim as duas usam o valor anterior das tentativas.
     * O {@code else} repete a coluna, que pelo {@code where} é nula, porque o Hibernate não deduz o
     * tipo de um {@code else null} literal.</p>
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CartaoEntity c
               set c.bloqueadoAte = case when c.tentativasSenha + 1 >= :limite then :bloqueioAte else c.bloqueadoAte end,
                   c.tentativasSenha = c.tentativasSenha + 1
             where c.numero = :numero
               and c.bloqueadoAte is null
            """)
    int registrarSenhaErrada(@Param("numero") String numero,
                             @Param("limite") int limite,
                             @Param("bloqueioAte") Instant bloqueioAte);

    /**
     * Zera a contagem se o cartão não estiver bloqueado. Se senhas erradas simultâneas o bloquearam
     * antes desta instrução, nada é alterado.
     *
     * @return 1 se zerou; 0 se o cartão está bloqueado
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CartaoEntity c
               set c.tentativasSenha = 0,
                   c.bloqueadoAte = null
             where c.numero = :numero
               and (c.bloqueadoAte is null or c.bloqueadoAte <= :agora)
            """)
    int registrarSenhaCerta(@Param("numero") String numero, @Param("agora") Instant agora);
}
