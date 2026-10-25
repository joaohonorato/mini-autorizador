-- Resultado das transações enviadas com Idempotency-Key, para que um reenvio devolva a resposta original.
-- impressao: SHA-256 do cartão e do valor; detecta a chave reutilizada em outra requisição.
-- resultado: OK ou o motivo da recusa.
CREATE TABLE transacao_idempotente (
    chave     VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    impressao CHAR(64)    NOT NULL,
    resultado VARCHAR(30) NULL,
    criada_em DATETIME(6) NOT NULL,
    PRIMARY KEY (chave),
    KEY ix_transacao_idempotente_criada_em (criada_em)
);
