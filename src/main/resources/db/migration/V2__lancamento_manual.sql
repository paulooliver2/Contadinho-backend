-- HU-47: compras da fatura digitadas à mão, além das importadas do arquivo.
-- Lançamento manual não tem importação nem id externo; o do arquivo tem os dois (RN-11).
-- Quando uma compra do arquivo é confirmada como a mesma de uma digitada (RN-59), a digitada vira ARQUIVO.

ALTER TABLE lancamento ADD COLUMN origem VARCHAR(7) NOT NULL DEFAULT 'ARQUIVO'
    CHECK (origem IN ('ARQUIVO', 'MANUAL'));
ALTER TABLE lancamento ALTER COLUMN origem DROP DEFAULT;

ALTER TABLE lancamento ALTER COLUMN importacao_id DROP NOT NULL;
ALTER TABLE lancamento ALTER COLUMN id_externo DROP NOT NULL;

ALTER TABLE lancamento ADD CONSTRAINT lancamento_origem_ck CHECK (
    (origem = 'MANUAL') = (importacao_id IS NULL)
    AND (origem = 'MANUAL') = (id_externo IS NULL)
);
