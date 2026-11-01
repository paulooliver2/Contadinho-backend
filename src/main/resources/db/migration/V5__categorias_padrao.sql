-- HU-15.1: quem já tinha conta ganha as categorias padrão; as contas novas ganham no cadastro (CategoriaServico).
INSERT INTO categoria (id, usuario_id, nome, essencial)
SELECT gen_random_uuid(), u.id, p.nome, p.essencial
FROM usuario u
CROSS JOIN (VALUES
    ('Moradia', true), ('Educação', true), ('Saúde', true), ('Contas', true), ('Mercado', true),
    ('Transporte', true), ('Delivery', false), ('Lazer', false), ('Compras', false), ('Assinaturas', false),
    ('Viagem', false), ('Outros', false)
) AS p (nome, essencial)
WHERE NOT EXISTS (SELECT 1 FROM categoria c WHERE c.usuario_id = u.id);
