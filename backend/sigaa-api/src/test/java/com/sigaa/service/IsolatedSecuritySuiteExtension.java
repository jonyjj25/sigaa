package com.sigaa.service;

import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Opt-in para ejecutar toda la suite sin .env, datos, archivos ni SMTP reales. */
public final class IsolatedSecuritySuiteExtension implements BeforeAllCallback {
    private static SuiteResource resource;
    public IsolatedSecuritySuiteExtension() throws Exception {
        if (Boolean.getBoolean("sigaa.security.validation")) initialize();
    }
    private static synchronized void initialize() throws Exception {
        if (resource != null) return;
        var fixture = new LeastPrivilegePostgresFixture();
        resource = new SuiteResource(fixture);
        System.setProperty("spring.config.import", "");
        System.setProperty("sigaa.test.codigo.url", fixture.url);
        System.setProperty("sigaa.test.codigo.user", "sigaa_a05_test");
        System.setProperty("sigaa.test.codigo.password", LeastPrivilegePostgresFixture.ADMIN_PASSWORD);
        System.setProperty("spring.datasource.url", fixture.url);
        System.setProperty("spring.datasource.username", "sigaa_runtime");
        System.setProperty("spring.datasource.password", LeastPrivilegePostgresFixture.RUNTIME_PASSWORD);
        System.setProperty("app.backup.datasource.url", fixture.url);
        System.setProperty("app.backup.datasource.username", "sigaa_backup");
        System.setProperty("app.backup.datasource.password", LeastPrivilegePostgresFixture.BACKUP_PASSWORD);
        System.setProperty("app.backup.directory", Files.createDirectories(fixture.postgres.root.resolve("backups")).toString());
        System.setProperty("app.backup.external-directory", Files.createDirectories(fixture.postgres.root.resolve("external")).toString());
        System.setProperty("app.backup.encryption-key-base64", Base64.getEncoder().encodeToString(new byte[32]));
        System.setProperty("spring.mail.host", "127.0.0.1");
        System.setProperty("spring.mail.port", "1");
        System.setProperty("jwt.secret", "TEST_ONLY_security_suite_secret_1234567890");
        System.setProperty("management.server.port", "0");
    }
    @Override public void beforeAll(ExtensionContext context) {
        if (resource != null) context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put("sigaa-isolated-security", resource);
    }
    private record SuiteResource(LeastPrivilegePostgresFixture fixture) implements AutoCloseable {
        @Override public void close() throws Exception { fixture.close(); }
    }
}
