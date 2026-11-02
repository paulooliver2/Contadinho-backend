-- HU-44: a compra digitada que uma importação casou (RN-59) volta a ser digitada quando a importação é desfeita,
-- em vez de sair junto com as compras do arquivo. As casadas antes desta migração não têm como ser reconhecidas.
ALTER TABLE lancamento ADD COLUMN digitado_antes BOOLEAN NOT NULL DEFAULT false;
