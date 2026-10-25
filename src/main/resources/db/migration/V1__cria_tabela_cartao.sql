CREATE TABLE IF NOT EXISTS cartao (
    numero VARCHAR(19)    NOT NULL,
    senha  VARCHAR(100)   NOT NULL,
    saldo  DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (numero)
);
