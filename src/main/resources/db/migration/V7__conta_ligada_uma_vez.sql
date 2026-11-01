-- HU-14 (RN-23): um lançamento da fatura paga no máximo uma conta fixa.
CREATE UNIQUE INDEX ocorrencia_conta_lancamento_uk ON ocorrencia_conta (lancamento_id);
