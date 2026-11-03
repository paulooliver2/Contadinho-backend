-- HU-30: a carteira. A posição é o que identifica o ativo; quantidade, preço médio e aplicado saem das
-- movimentações (RNF-05, RN-48). Tudo sai junto com a conta (HU-04).

CREATE TABLE investimento (
    id           UUID PRIMARY KEY,
    usuario_id   UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo         VARCHAR(12)   NOT NULL CHECK (tipo IN ('ACAO', 'CRIPTO', 'TESOURO', 'RENDA_FIXA')),
    -- Ticker (PETR4), id da CoinGecko (bitcoin), nome oficial do título (Tesouro IPCA+ 2035) ou o emissor.
    codigo       VARCHAR(80)   NOT NULL,
    nome         VARCHAR(120),
    simbolo      VARCHAR(20),
    produto      VARCHAR(3)    CHECK (produto IN ('CDB', 'LCI', 'LCA')),
    indexador    VARCHAR(10)   CHECK (indexador IN ('CDI', 'PREFIXADO')),
    -- 110 = 110% do CDI; 13.5 = 13,5% ao ano.
    taxa         NUMERIC(7,4)  CHECK (taxa > 0 AND taxa <= 1000),
    vencimento   DATE,
    conferido    BOOLEAN       NOT NULL,
    criado_em    TIMESTAMPTZ   NOT NULL,
    CHECK ((tipo = 'RENDA_FIXA') = (produto IS NOT NULL AND indexador IS NOT NULL AND taxa IS NOT NULL
        AND vencimento IS NOT NULL))
);

-- Ação, cripto e título não se repetem: mais do mesmo é aporte. Cada aplicação de renda fixa é uma posição.
CREATE UNIQUE INDEX investimento_unico ON investimento (usuario_id, tipo, codigo) WHERE tipo <> 'RENDA_FIXA';
CREATE INDEX investimento_usuario ON investimento (usuario_id);

CREATE TABLE movimentacao_investimento (
    id               UUID PRIMARY KEY,
    investimento_id  UUID          NOT NULL REFERENCES investimento (id) ON DELETE CASCADE,
    usuario_id       UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo             VARCHAR(8)    NOT NULL CHECK (tipo IN ('INICIAL', 'APORTE', 'RESGATE')),
    data             DATE          NOT NULL,
    quantidade       NUMERIC(20,8) CHECK (quantidade > 0),
    preco            NUMERIC(20,8) CHECK (preco > 0),
    -- Total em reais: quantidade × preço, o valor do Tesouro ou o da renda fixa. Nulo no resgate por quantidade.
    valor            NUMERIC(12,2) CHECK (valor > 0),
    -- Só no resgate da renda fixa: "Resgatei tudo" encerra a posição.
    total            BOOLEAN       NOT NULL DEFAULT FALSE,
    criado_em        TIMESTAMPTZ   NOT NULL,
    CHECK (quantidade IS NOT NULL OR valor IS NOT NULL)
);

CREATE INDEX movimentacao_investimento_posicao ON movimentacao_investimento (investimento_id);
