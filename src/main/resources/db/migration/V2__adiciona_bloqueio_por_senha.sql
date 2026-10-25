-- Bloqueio do cartão depois de seguidas senhas erradas (PoliticaDeBloqueio).
-- tentativas_senha: senhas erradas seguidas desde a última senha certa.
-- bloqueado_ate: fim do bloqueio, em UTC; nulo quando o cartão não está bloqueado.
ALTER TABLE cartao
    ADD COLUMN tentativas_senha INT         NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_ate    DATETIME(6) NULL;
