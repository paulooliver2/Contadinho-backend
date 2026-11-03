-- HU-32: até onde a planilha do Tesouro já foi lida para cada título, para o passado de um título não depender
-- do que outro dono já buscou.
ALTER TABLE cotacao ADD COLUMN historico_desde DATE;
