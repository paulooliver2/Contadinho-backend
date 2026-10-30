-- Esquema do MVP (épicos 1 a 6). Referências: docs/Contadinho — Histórias de usuário.md
-- Convenções:
--   * Dinheiro em NUMERIC(12,2) (RNF-04). Nunca ponto flutuante.
--   * Toda tabela de negócio tem usuario_id, e toda consulta filtra por ele (RN-01, RNF-01).
--   * Nenhum total, saldo ou status calculável é guardado (RN-39, RNF-05).
--   * Datas de negócio são DATE no fuso America/Sao_Paulo; instantes são TIMESTAMPTZ.

-- ---------------------------------------------------------------- conta

CREATE TABLE usuario (
    id                    UUID PRIMARY KEY,
    nome                  VARCHAR(80)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    senha_hash            VARCHAR(100) NOT NULL,
    pix_tipo              VARCHAR(10)  CHECK (pix_tipo IN ('CPF', 'EMAIL', 'CELULAR', 'ALEATORIA')),
    pix_chave             VARCHAR(77),
    modelo_cobranca       TEXT,
    exclusao_pedida_em    TIMESTAMPTZ,
    criado_em             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((pix_tipo IS NULL) = (pix_chave IS NULL))
);
CREATE UNIQUE INDEX usuario_email_uk ON usuario (lower(email));

-- Refresh tokens (HU-01.3). Guardamos só o hash.
CREATE TABLE sessao (
    id            UUID PRIMARY KEY,
    usuario_id    UUID         NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    token_hash    CHAR(64)     NOT NULL UNIQUE,
    dispositivo   VARCHAR(120),
    criada_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expira_em     TIMESTAMPTZ  NOT NULL,
    revogada_em   TIMESTAMPTZ
);
CREATE INDEX sessao_usuario_idx ON sessao (usuario_id);

-- Bloqueio após 5 erros em 15 minutos (RN-03). Por e-mail, exista a conta ou não.
CREATE TABLE tentativa_login (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(254) NOT NULL,
    sucesso      BOOLEAN      NOT NULL,
    ocorrida_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX tentativa_login_email_idx ON tentativa_login (lower(email), ocorrida_em);

-- Link de recuperação: 30 minutos, uso único (RN-04).
CREATE TABLE token_recuperacao (
    id           UUID PRIMARY KEY,
    usuario_id   UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    token_hash   CHAR(64)    NOT NULL UNIQUE,
    expira_em    TIMESTAMPTZ NOT NULL,
    usado_em     TIMESTAMPTZ
);

-- ---------------------------------------------------------------- avisos

CREATE TABLE preferencias_avisos (
    usuario_id            UUID PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    conta_ativo           BOOLEAN  NOT NULL DEFAULT true,
    conta_antecedencia    SMALLINT NOT NULL DEFAULT 2 CHECK (conta_antecedencia IN (1, 2, 3)),
    fatura_fechou_ativo   BOOLEAN  NOT NULL DEFAULT true,
    fatura_receber_ativo  BOOLEAN  NOT NULL DEFAULT true,
    fatura_antecedencia   SMALLINT NOT NULL DEFAULT 3 CHECK (fatura_antecedencia IN (2, 3, 5)),
    silencio_ativo        BOOLEAN  NOT NULL DEFAULT true,
    silencio_inicio       TIME     NOT NULL DEFAULT '22:00',
    silencio_fim          TIME     NOT NULL DEFAULT '08:00'
);

-- ---------------------------------------------------------------- pessoas

CREATE TABLE pessoa (
    id            UUID PRIMARY KEY,
    usuario_id    UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    nome          VARCHAR(80) NOT NULL,
    apelido       VARCHAR(40),
    whatsapp      VARCHAR(14) CHECK (whatsapp ~ '^\+55[0-9]{10,11}$'),   -- RN-35
    arquivada_em  TIMESTAMPTZ,
    criada_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX pessoa_usuario_idx ON pessoa (usuario_id);

-- ---------------------------------------------------------------- categorias

CREATE TABLE categoria (
    id            UUID PRIMARY KEY,
    usuario_id    UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    nome          VARCHAR(40) NOT NULL,
    essencial     BOOLEAN     NOT NULL DEFAULT false,   -- padrão da RN-28
    arquivada_em  TIMESTAMPTZ
);
CREATE UNIQUE INDEX categoria_nome_uk ON categoria (usuario_id, lower(nome));

-- ---------------------------------------------------------------- cartões e faturas

CREATE TABLE cartao (
    id               UUID PRIMARY KEY,
    usuario_id       UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    apelido          VARCHAR(40) NOT NULL,
    banco            VARCHAR(30) NOT NULL,
    ultimos4         CHAR(4)     NOT NULL CHECK (ultimos4 ~ '^[0-9]{4}$'),   -- RN-07: nunca o número completo
    dia_fechamento   SMALLINT    NOT NULL CHECK (dia_fechamento BETWEEN 1 AND 31),   -- RN-08
    dia_vencimento   SMALLINT    NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 31),
    mapeamento_csv   JSONB,                                                  -- HU-37
    encerrado_em     TIMESTAMPTZ,                                            -- RN-55
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX cartao_usuario_idx ON cartao (usuario_id);

CREATE TABLE cartao_adicional (
    id          UUID PRIMARY KEY,
    usuario_id  UUID    NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    cartao_id   UUID    NOT NULL REFERENCES cartao (id) ON DELETE CASCADE,
    ultimos4    CHAR(4) NOT NULL CHECK (ultimos4 ~ '^[0-9]{4}$'),
    pessoa_id   UUID    NOT NULL REFERENCES pessoa (id),
    UNIQUE (cartao_id, ultimos4)
);

-- Uma fatura por cartão e vencimento (RN-09).
CREATE TABLE fatura (
    id           UUID PRIMARY KEY,
    usuario_id   UUID NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    cartao_id    UUID NOT NULL REFERENCES cartao (id) ON DELETE CASCADE,
    fechamento   DATE NOT NULL,
    vencimento   DATE NOT NULL,
    UNIQUE (cartao_id, vencimento)
);

CREATE TABLE importacao (
    id             UUID PRIMARY KEY,
    usuario_id     UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    fatura_id      UUID          NOT NULL REFERENCES fatura (id) ON DELETE CASCADE,
    nome_arquivo   VARCHAR(255)  NOT NULL,
    formato        VARCHAR(3)    NOT NULL CHECK (formato IN ('OFX', 'CSV')),   -- RN-10
    modelo         VARCHAR(40)   NOT NULL,                                     -- modelo do banco, genérico ou mapeamento
    total_arquivo  NUMERIC(12,2),                                              -- RN-12
    importada_em   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE lancamento (
    id                      UUID PRIMARY KEY,
    usuario_id              UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    cartao_id               UUID          NOT NULL REFERENCES cartao (id) ON DELETE CASCADE,
    fatura_id               UUID          NOT NULL REFERENCES fatura (id) ON DELETE CASCADE,
    importacao_id           UUID          NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,   -- RN-54
    id_externo              VARCHAR(128)  NOT NULL,          -- FITID ou hash do CSV (RN-11)
    data                    DATE          NOT NULL,
    descricao               VARCHAR(255)  NOT NULL,
    descricao_normalizada   VARCHAR(255)  NOT NULL,
    valor                   NUMERIC(12,2) NOT NULL,          -- negativo = estorno (RN-20)
    parcela_atual           SMALLINT,
    parcela_total           SMALLINT,
    final_cartao            CHAR(4),                         -- RN-19
    categoria_id            UUID REFERENCES categoria (id),
    essencial               BOOLEAN,                         -- NULL = padrão da categoria (RN-28)
    pessoa_id               UUID REFERENCES pessoa (id),     -- NULL = do dono (RN-15)
    sugerido                BOOLEAN       NOT NULL DEFAULT false,   -- RN-18
    estorno_de_id           UUID REFERENCES lancamento (id) ON DELETE SET NULL,
    UNIQUE (cartao_id, id_externo),                          -- RN-11, RNF-04
    CHECK ((parcela_atual IS NULL) = (parcela_total IS NULL)),
    CHECK (parcela_atual IS NULL OR parcela_atual BETWEEN 1 AND parcela_total)
);
CREATE INDEX lancamento_fatura_idx ON lancamento (fatura_id);
CREATE INDEX lancamento_usuario_data_idx ON lancamento (usuario_id, data);
CREATE INDEX lancamento_descricao_idx ON lancamento (usuario_id, descricao_normalizada);

-- Responsável aplicado às próximas parcelas da mesma compra (RN-17).
CREATE TABLE regra_parcela (
    id                      UUID PRIMARY KEY,
    usuario_id              UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    cartao_id               UUID          NOT NULL REFERENCES cartao (id) ON DELETE CASCADE,
    descricao_normalizada   VARCHAR(255)  NOT NULL,
    valor                   NUMERIC(12,2) NOT NULL,
    parcela_total           SMALLINT      NOT NULL,
    pessoa_id               UUID REFERENCES pessoa (id),
    a_partir_da_parcela     SMALLINT      NOT NULL
);

-- ---------------------------------------------------------------- gastos

CREATE TABLE conta_fixa (
    id                UUID PRIMARY KEY,
    usuario_id        UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    descricao         VARCHAR(80) NOT NULL,
    categoria_id      UUID        NOT NULL REFERENCES categoria (id),
    dia_vencimento    SMALLINT    NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 31),
    forma_pagamento   VARCHAR(20) NOT NULL CHECK (forma_pagamento IN ('PIX_BOLETO', 'DEBITO_AUTOMATICO', 'CARTAO')),
    cartao_id         UUID REFERENCES cartao (id),
    essencial         BOOLEAN,
    inicio            DATE        NOT NULL,   -- primeiro dia do mês da primeira ocorrência
    encerrada_em      DATE,                   -- RN-21: para de gerar no mês seguinte
    CHECK ((forma_pagamento = 'CARTAO') = (cartao_id IS NOT NULL))   -- RN-22
);

-- Valor que vale do mês dado em diante (HU-11.3).
CREATE TABLE conta_fixa_valor (
    id              UUID PRIMARY KEY,
    conta_fixa_id   UUID          NOT NULL REFERENCES conta_fixa (id) ON DELETE CASCADE,
    vigente_desde   DATE          NOT NULL,
    valor           NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    UNIQUE (conta_fixa_id, vigente_desde)
);

-- Status da RN-57 é derivado: cartão → lancamento_id nulo ou não; fora do cartão → pago_em nulo ou não.
CREATE TABLE ocorrencia_conta (
    id              UUID PRIMARY KEY,
    usuario_id      UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    conta_fixa_id   UUID          NOT NULL REFERENCES conta_fixa (id) ON DELETE CASCADE,
    competencia     DATE          NOT NULL,   -- primeiro dia do mês
    vencimento      DATE          NOT NULL,   -- pode ser ajustado só neste mês (HU-46.2)
    valor_previsto  NUMERIC(12,2) NOT NULL CHECK (valor_previsto > 0),
    valor_pago      NUMERIC(12,2) CHECK (valor_pago > 0),                     -- RN-25
    pago_em         DATE,
    lancamento_id   UUID REFERENCES lancamento (id) ON DELETE SET NULL,       -- RN-23
    UNIQUE (conta_fixa_id, competencia),
    CHECK ((valor_pago IS NULL) = (pago_em IS NULL))
);
CREATE INDEX ocorrencia_conta_usuario_idx ON ocorrencia_conta (usuario_id, vencimento);

CREATE TABLE gasto (
    id                UUID PRIMARY KEY,
    usuario_id        UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    descricao         VARCHAR(80)   NOT NULL,
    valor             NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    data              DATE          NOT NULL,
    categoria_id      UUID          NOT NULL REFERENCES categoria (id),
    forma_pagamento   VARCHAR(20)   NOT NULL CHECK (forma_pagamento IN ('PIX_BOLETO', 'DEBITO', 'CARTAO')),
    cartao_id         UUID REFERENCES cartao (id),
    essencial         BOOLEAN,
    lancamento_id     UUID REFERENCES lancamento (id) ON DELETE SET NULL,   -- RN-26
    CHECK ((forma_pagamento = 'CARTAO') = (cartao_id IS NOT NULL))
);
CREATE INDEX gasto_usuario_data_idx ON gasto (usuario_id, data);

-- ---------------------------------------------------------------- receitas

CREATE TABLE receita (
    id             UUID PRIMARY KEY,
    usuario_id     UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    descricao      VARCHAR(80)   NOT NULL,
    valor          NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    recorrente     BOOLEAN       NOT NULL,
    dia            SMALLINT      NOT NULL CHECK (dia BETWEEN 1 AND 31),
    inicio         DATE          NOT NULL,
    encerrada_em   DATE
);

CREATE TABLE ocorrencia_receita (
    id               UUID PRIMARY KEY,
    usuario_id       UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    receita_id       UUID          NOT NULL REFERENCES receita (id) ON DELETE CASCADE,
    competencia      DATE          NOT NULL,
    data             DATE          NOT NULL,
    valor_previsto   NUMERIC(12,2) NOT NULL CHECK (valor_previsto > 0),
    valor_recebido   NUMERIC(12,2) CHECK (valor_recebido > 0),   -- HU-22.2
    UNIQUE (receita_id, competencia)
);

-- ---------------------------------------------------------------- a receber
-- Saldo da pessoa = soma dos recebíveis − soma dos pagamentos não desfeitos (RN-29).
-- Os pagamentos abatem do recebível mais antigo (RN-30) no cálculo; saldo negativo é crédito (RN-16).

CREATE TABLE emprestimo (
    id                   UUID PRIMARY KEY,
    usuario_id           UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    pessoa_id            UUID          NOT NULL REFERENCES pessoa (id),
    descricao            VARCHAR(80),
    valor                NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    parcelas             SMALLINT      NOT NULL CHECK (parcelas IN (1, 2, 3, 6)),   -- HU-17
    primeira_devolucao   DATE,                                                      -- NULL = "Sem data" (RN-32)
    criado_em            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE recebivel (
    id              UUID PRIMARY KEY,
    usuario_id      UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    pessoa_id       UUID          NOT NULL REFERENCES pessoa (id),
    lancamento_id   UUID          UNIQUE REFERENCES lancamento (id) ON DELETE CASCADE,   -- RN-16, RN-54
    emprestimo_id   UUID          REFERENCES emprestimo (id) ON DELETE CASCADE,
    parcela         SMALLINT,
    valor           NUMERIC(12,2) NOT NULL,       -- negativo quando é estorno (RN-20)
    vencimento      DATE,
    CHECK ((lancamento_id IS NULL) <> (emprestimo_id IS NULL))
);
CREATE INDEX recebivel_pessoa_idx ON recebivel (pessoa_id, vencimento);

CREATE TABLE pagamento (
    id            UUID PRIMARY KEY,
    usuario_id    UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    pessoa_id     UUID          NOT NULL REFERENCES pessoa (id),
    valor         NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    recebido_em   DATE          NOT NULL,
    desfeito_em   TIMESTAMPTZ,                    -- HU-19.3
    criado_em     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX pagamento_pessoa_idx ON pagamento (pessoa_id);

-- "Cobrado há X dias" (HU-20.4).
CREATE TABLE cobranca (
    id           UUID PRIMARY KEY,
    usuario_id   UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    pessoa_id    UUID          NOT NULL REFERENCES pessoa (id) ON DELETE CASCADE,
    valor        NUMERIC(12,2) NOT NULL,
    enviada_em   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX cobranca_pessoa_idx ON cobranca (pessoa_id, enviada_em);
