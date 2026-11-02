-- Ejecutar exclusivamente en la base dedicada a SIGAA, como administrador.
-- No cambia contraseñas de roles existentes ni usa REASSIGN OWNED global.
\set ON_ERROR_STOP on
\getenv runtime_password APP_DB_PASSWORD
\getenv backup_password BACKUP_DB_PASSWORD
BEGIN;
SELECT format('CREATE ROLE sigaa_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT PASSWORD %L', :'runtime_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sigaa_runtime') \gexec
SELECT format('CREATE ROLE sigaa_backup LOGIN NOSUPERUSER CREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT PASSWORD %L', :'backup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sigaa_backup') \gexec

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('sigaa_runtime','sigaa_backup')
               AND (rolsuper OR rolcreaterole OR rolbypassrls OR NOT rolcanlogin OR rolinherit
                    OR (rolname <> 'sigaa_backup' AND rolcreatedb)
                    OR (rolname = 'sigaa_backup' AND NOT rolcreatedb)))
       OR EXISTS (SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.member
                  WHERE r.rolname IN ('sigaa_runtime','sigaa_backup')) THEN
        RAISE EXCEPTION 'Los roles existentes no cumplen la política de mínimo privilegio';
    END IF;
END $$;
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC, sigaa_runtime, sigaa_backup', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO sigaa_runtime, sigaa_backup', current_database()) \gexec
REVOKE ALL ON SCHEMA public FROM PUBLIC, sigaa_runtime, sigaa_backup;
GRANT USAGE ON SCHEMA public TO sigaa_runtime, sigaa_backup;
COMMIT;
