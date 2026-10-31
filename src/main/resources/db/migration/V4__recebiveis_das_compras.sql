-- RN-16: compras atribuídas antes de existir o recebível ganham o delas, com o vencimento da fatura.
INSERT INTO recebivel (id, usuario_id, pessoa_id, lancamento_id, valor, vencimento)
SELECT gen_random_uuid(), l.usuario_id, l.pessoa_id, l.id, l.valor, f.vencimento
FROM lancamento l
JOIN fatura f ON f.id = l.fatura_id
WHERE l.pessoa_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM recebivel r WHERE r.lancamento_id = l.id);
