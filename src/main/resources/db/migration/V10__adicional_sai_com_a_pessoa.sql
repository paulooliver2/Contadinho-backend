-- HU-38: excluir uma pessoa (só possível sem histórico) leva junto os adicionais dela.
ALTER TABLE cartao_adicional DROP CONSTRAINT cartao_adicional_pessoa_id_fkey;
ALTER TABLE cartao_adicional ADD CONSTRAINT cartao_adicional_pessoa_id_fkey
    FOREIGN KEY (pessoa_id) REFERENCES pessoa (id) ON DELETE CASCADE;
