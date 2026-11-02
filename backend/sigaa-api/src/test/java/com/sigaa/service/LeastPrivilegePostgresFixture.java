package com.sigaa.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Solo crea un cluster nuevo bajo target. Nunca lee .env ni conecta una base existente. */
final class LeastPrivilegePostgresFixture implements AutoCloseable {
    static final String RUNTIME_PASSWORD = "TEST_ONLY_runtime_A08_123!";
    static final String BACKUP_PASSWORD = "TEST_ONLY_backup_A08_789!";
    static final String ADMIN_PASSWORD = "TEST_ONLY_bootstrap_A08_012!";
    final PasswordResetConcurrencyIntegrationTest.EphemeralPostgres postgres;
    final String url;
    final Path project = Path.of(System.getProperty("sigaa.test.project.root", ".")).toAbsolutePath().normalize();

    LeastPrivilegePostgresFixture() throws Exception {
        postgres = new PasswordResetConcurrencyIntegrationTest.EphemeralPostgres();
        try {
            execute(postgres.dataSource, "CREATE DATABASE sigaa_security");
            url = postgres.dataSource.getUrl().split("\\?")[0].replace("/postgres", "/sigaa_security");
            provision(RUNTIME_PASSWORD, BACKUP_PASSWORD);
            // Autenticación SCRAM real, con contraseñas exclusivamente ficticias.
            execute(postgres.dataSource, "ALTER ROLE sigaa_a05_test PASSWORD '" + ADMIN_PASSWORD + "'");
            Files.writeString(postgres.root.resolve("data/pg_hba.conf"),
                    "host all all 127.0.0.1/32 scram-sha-256\nlocal all all trust\n");
            postgres.dataSource.setPassword(ADMIN_PASSWORD);
            execute(postgres.dataSource, "SELECT pg_reload_conf()");
            installSchema();
        } catch (Exception failure) { postgres.close(); throw failure; }
    }

    void provision(String runtime, String backup) throws Exception {
        var process = new ProcessBuilder(postgres.tool("psql").toString(), "--no-password", "--host=127.0.0.1",
                "--port=" + java.net.URI.create(url.substring(5)).getPort(), "--username=sigaa_a05_test",
                "--dbname=sigaa_security", "--set=ON_ERROR_STOP=1",
                "--file=" + project.resolve("../../ops/postgres/provision-roles.sql").normalize())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        process.environment().putAll(Map.of("APP_DB_PASSWORD", runtime,
                "BACKUP_DB_PASSWORD", backup, "PGPASSWORD", ADMIN_PASSWORD,
                "PGOPTIONS", "-c log_statement=none -c log_min_error_statement=panic"));
        Process child = process.start();
        if (!child.waitFor(30, TimeUnit.SECONDS)) { child.destroyForcibly(); throw new IllegalStateException("Timeout de provisionamiento ficticio"); }
        if (child.exitValue() != 0) throw new IllegalStateException("Falló el provisionamiento ficticio de roles");
    }

    void installSchema() throws Exception {
        execute(owner(), Files.readString(project.resolve("src/main/resources/db/schema.sql")));
    }

    DriverManagerDataSource source(String user, String password) { return new DriverManagerDataSource(url, user, password); }
    DriverManagerDataSource runtime() { return source("sigaa_runtime", RUNTIME_PASSWORD); }
    DriverManagerDataSource owner() { return source("sigaa_a05_test", ADMIN_PASSWORD); }
    DriverManagerDataSource backup() { return source("sigaa_backup", BACKUP_PASSWORD); }
    static void execute(javax.sql.DataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute(sql); }
    }
    @Override public void close() throws Exception { postgres.close(); }
}
