-- RN-26: um lançamento da fatura paga no máximo um gasto avulso.
CREATE UNIQUE INDEX gasto_lancamento_uk ON gasto (lancamento_id);
