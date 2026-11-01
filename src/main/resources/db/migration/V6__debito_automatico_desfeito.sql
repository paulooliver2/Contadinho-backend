-- RN-24: o débito automático é marcado como pago no vencimento. Quando o dono desfaz, a ocorrência guarda isso
-- para o app não marcar de novo.
ALTER TABLE ocorrencia_conta ADD COLUMN automatico_desfeito BOOLEAN NOT NULL DEFAULT false;
