-- HU-39: a fatura também pode ser importada do PDF do banco (RN-10).

ALTER TABLE importacao DROP CONSTRAINT importacao_formato_check;
ALTER TABLE importacao ADD CONSTRAINT importacao_formato_check CHECK (formato IN ('OFX', 'CSV', 'PDF'));
