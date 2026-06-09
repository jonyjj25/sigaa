ALTER TABLE usuarios
    ADD COLUMN roles VARCHAR(100) NOT NULL DEFAULT 'miembros';

UPDATE usuarios SET roles = 'admin' WHERE correo = 'admin@sigaa.com';
UPDATE usuarios SET roles = 'miembros' WHERE roles IS NULL OR roles = '';