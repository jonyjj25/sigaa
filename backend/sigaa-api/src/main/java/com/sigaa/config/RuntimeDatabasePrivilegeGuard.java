package com.sigaa.config;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Impide arrancar producción con una conexión habitual administrativa o propietaria. */
@Component
@Profile("prod")
public class RuntimeDatabasePrivilegeGuard implements SmartInitializingSingleton {
    private final DataSource dataSource;
    public RuntimeDatabasePrivilegeGuard(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public void afterSingletonsInstantiated() {
        String sql = """
                SELECT r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolbypassrls
                    OR EXISTS (SELECT 1 FROM pg_auth_members WHERE member = r.oid)
                    OR EXISTS (SELECT 1 FROM pg_database d WHERE d.datdba = r.oid)
                    OR has_database_privilege(current_database(), 'CREATE')
                    OR has_database_privilege(current_database(), 'TEMP')
                    OR has_schema_privilege('public', 'CREATE')
                    OR EXISTS (SELECT 1 FROM pg_namespace n WHERE n.nspname !~ '^pg_' AND n.nspname <> 'information_schema'
                               AND (n.nspowner = r.oid OR has_schema_privilege(n.oid, 'CREATE')))
                    OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                               WHERE n.nspname !~ '^pg_' AND n.nspname <> 'information_schema' AND c.relowner = r.oid)
                    OR EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                               WHERE n.nspname !~ '^pg_' AND n.nspname <> 'information_schema' AND p.proowner = r.oid)
                FROM pg_roles r WHERE r.rolname = current_user
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var result = statement.executeQuery(sql)) {
                if (!result.next() || result.getBoolean(1)) throw new IllegalStateException(
                        "La conexión habitual de SIGAA no cumple mínimo privilegio; revise los roles de despliegue.");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudieron verificar los privilegios de la conexión habitual de SIGAA.");
        }
    }
}
