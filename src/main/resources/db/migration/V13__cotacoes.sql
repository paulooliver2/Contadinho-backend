-- HU-31: o que vem de fora para dar o valor de hoje. Preço de mercado não é dado pessoal: uma linha por ativo,
-- compartilhada entre donos, para gastar menos da cota das fontes.

CREATE TABLE cotacao (
    id             UUID PRIMARY KEY,
    tipo           VARCHAR(12)    NOT NULL CHECK (tipo IN ('ACAO', 'CRIPTO', 'TESOURO')),
    codigo         VARCHAR(80)    NOT NULL,
    -- Nulos até a primeira busca que der certo.
    preco          NUMERIC(20,8)  CHECK (preco >= 0),
    referencia     DATE,
    atualizado_em  TIMESTAMPTZ,
    -- A última tentativa que falhou; nula quando a última deu certo (RN-47).
    falhou_em      TIMESTAMPTZ,
    fonte          VARCHAR(20)    NOT NULL,
    UNIQUE (tipo, codigo)
);

-- Série 12 do SGS (Banco Central): o CDI do dia em % ao dia. Os dias guardados são o calendário de dias úteis.
CREATE TABLE cdi_diario (
    data  DATE PRIMARY KEY,
    taxa  NUMERIC(12,8) NOT NULL
);

-- Quando cada fonte diária foi tentada (tesouro, cdi) e, no CDI, desde que dia a tabela cobre.
CREATE TABLE atualizacao_fonte (
    fonte        VARCHAR(20) PRIMARY KEY,
    tentou_em    TIMESTAMPTZ NOT NULL,
    falhou       BOOLEAN     NOT NULL,
    cobre_desde  DATE
);
