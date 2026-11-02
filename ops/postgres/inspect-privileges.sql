-- Solo metadatos, ejecutar como cada conexión de SIGAA antes y después del cambio.
SELECT current_user AS role, r.rolsuper, r.rolcreatedb, r.rolcreaterole, r.rolbypassrls, r.rolinherit,
       pg_get_userbyid(d.datdba) AS database_owner,
       has_database_privilege(current_database(), 'CONNECT') AS can_connect,
       has_database_privilege(current_database(), 'CREATE') AS can_create_schema,
       has_database_privilege(current_database(), 'TEMP') AS can_create_temp,
       has_schema_privilege('public', 'USAGE') AS schema_usage,
       has_schema_privilege('public', 'CREATE') AS schema_create
FROM pg_roles r JOIN pg_database d ON d.datname = current_database()
WHERE r.rolname = current_user;
SELECT COUNT(*) AS owned_databases FROM pg_database d JOIN pg_roles r ON r.oid = d.datdba WHERE r.rolname = current_user;
SELECT parent.rolname AS granted_role
FROM pg_auth_members m JOIN pg_roles parent ON parent.oid = m.roleid
JOIN pg_roles member ON member.oid = m.member WHERE member.rolname = current_user;
SELECT c.relkind, COUNT(*) AS objects,
       COUNT(*) FILTER (WHERE c.relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)) AS owned
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' GROUP BY c.relkind ORDER BY c.relkind;
SELECT COUNT(*) AS functions,
       COUNT(*) FILTER (WHERE p.proowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)) AS owned
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public';
