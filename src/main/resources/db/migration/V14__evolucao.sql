-- HU-32: um preço por ativo por dia, para calcular a evolução na leitura (RNF-05: nenhum total gravado).
-- Compartilhado entre donos, como a cotacao.

CREATE TABLE cotacao_diaria (
    tipo    VARCHAR(12)    NOT NULL CHECK (tipo IN ('ACAO', 'CRIPTO', 'TESOURO')),
    codigo  VARCHAR(80)    NOT NULL,
    data    DATE           NOT NULL,
    preco   NUMERIC(20,8)  NOT NULL CHECK (preco >= 0),
    PRIMARY KEY (tipo, codigo, data)
);

-- O preço que já existe vira o primeiro dia do histórico.
INSERT INTO cotacao_diaria (tipo, codigo, data, preco)
SELECT tipo, codigo, referencia, preco FROM cotacao WHERE preco IS NOT NULL AND referencia IS NOT NULL;

-- Ação ou FII, para a distribuição por classe. Só faz sentido em tipo = 'ACAO'.
ALTER TABLE investimento ADD COLUMN fii BOOLEAN NOT NULL DEFAULT false;
