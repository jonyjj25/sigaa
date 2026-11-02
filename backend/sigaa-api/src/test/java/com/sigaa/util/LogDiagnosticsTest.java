package com.sigaa.util;

import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogDiagnosticsTest {
    @Test
    void keepsFailureTypesAndCodeLocationWithoutMessagesPathsOrSuppressedValues() {
        SQLException cause = new SQLException("TEST_ONLY_SQL_AND_PARAMETER");
        RuntimeException failure = new RuntimeException("TEST_ONLY_PRIVATE_VALUE", cause);
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                "com.sigaa.service.ExampleService", "save", "C:/private/TEST_ONLY.pdf", 42)});
        failure.addSuppressed(new IOException("TEST_ONLY_SUPPRESSED_VALUE"));

        String diagnostic = LogDiagnostics.describe(failure);

        assertTrue(diagnostic.contains("java.lang.RuntimeException"));
        assertTrue(diagnostic.contains("java.sql.SQLException"));
        assertTrue(diagnostic.contains("com.sigaa.service.ExampleService.save:42"));
        assertFalse(diagnostic.contains("TEST_ONLY"));
        assertFalse(diagnostic.contains("C:/private"));
        assertFalse(diagnostic.contains("\n"));
    }

    @Test
    void handlesCausesWithoutApplicationStackFrames() {
        IOException failure = new IOException("TEST_ONLY_PRIVATE_VALUE");
        failure.setStackTrace(new StackTraceElement[0]);

        assertTrue(LogDiagnostics.describe(failure).equals("java.io.IOException"));
    }

    @Test
    void boundsLongCauseChains() {
        Throwable failure = new IOException("TEST_ONLY_PRIVATE_VALUE");
        for (int i = 0; i < 20; i++) failure = new RuntimeException("TEST_ONLY_PRIVATE_VALUE", failure);

        String diagnostic = LogDiagnostics.describe(failure);

        assertTrue(diagnostic.endsWith("causas adicionales omitidas"));
        assertTrue(diagnostic.split(" <- ").length == 7);
        assertFalse(diagnostic.contains("TEST_ONLY"));
    }

    @Test
    void handlesCircularCauseChains() {
        RuntimeException first = new RuntimeException("TEST_ONLY_FIRST");
        RuntimeException second = new RuntimeException("TEST_ONLY_SECOND", first);
        first.initCause(second);

        String diagnostic = LogDiagnostics.describe(first);

        assertTrue(diagnostic.endsWith("causas adicionales omitidas"));
        assertTrue(diagnostic.split(" <- ").length == 3);
        assertFalse(diagnostic.contains("TEST_ONLY"));
    }
}
