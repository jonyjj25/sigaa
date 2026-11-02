package com.sigaa.service;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica el classpath parcial sin arrancar Spring, leer .env ni conectar una base. */
class AdminPasswordResetServiceClassLoadingTest {
    private static final String SERVICE = "com.sigaa.service.AdminPasswordResetService";
    @TempDir Path compiled;

    @Test void missingRequestViewReproducesTheStartupFailure() throws Exception {
        copyClass(SERVICE);
        copyClass(SERVICE + "$ApprovalResult");
        try (var loader = isolatedLoader()) {
            var service = loader.loadClass(SERVICE);
            var failure = assertThrows(NoClassDefFoundError.class, service::getDeclaredMethods);
            assertTrue(failure.getMessage().contains("$RequestView"));
        }
    }

    @Test void completeCompilationLoadsRecordsAndServiceMethods() throws Exception {
        for (String name : new String[]{SERVICE, SERVICE + "$RequestView", SERVICE + "$ApprovalResult"}) copyClass(name);
        try (var loader = isolatedLoader()) {
            var service = loader.loadClass(SERVICE);
            assertDoesNotThrow(service::getDeclaredMethods);
            assertTrue(loader.loadClass(SERVICE + "$RequestView").isRecord());
            assertTrue(service.getMethod("approve", Long.class, String.class, String.class).getReturnType().isRecord());
        }
    }

    private void copyClass(String name) throws Exception {
        String resource = name.replace('.', '/') + ".class";
        Path destination = compiled.resolve(resource);
        Files.createDirectories(destination.getParent());
        try (var bytes = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(bytes, "La compilación debe incluir las clases internas");
            Files.copy(bytes, destination);
        }
    }

    private URLClassLoader isolatedLoader() throws Exception {
        return new URLClassLoader(new URL[]{compiled.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(SERVICE) && !name.startsWith(SERVICE + "$")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
    }
}
