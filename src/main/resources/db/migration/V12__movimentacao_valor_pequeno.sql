-- HU-30 (revisão): um saldo pequeno de cripto (100 PEPE a R$ 0,00001234) dá menos de 1 centavo e vira 0,00.
ALTER TABLE movimentacao_investimento
    DROP CONSTRAINT movimentacao_investimento_valor_check,
    ADD CONSTRAINT movimentacao_investimento_valor_check CHECK (valor >= 0);
