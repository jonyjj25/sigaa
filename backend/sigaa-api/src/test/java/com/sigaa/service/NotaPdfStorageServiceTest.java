package com.sigaa.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotaPdfStorageServiceTest {
    @TempDir Path directory;
    private NotaPdfStorageService service;

    @BeforeEach
    void setUp() {
        service = new NotaPdfStorageService(directory);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void storesOnlyValidatedPdfAndKeepsItAfterCommit() throws Exception {
        byte[] content = "%PDF-1.7\nnota oficial".getBytes(StandardCharsets.UTF_8);
        NotaPdfStorageService.StoredPdf stored = service.guardar(
            new MockMultipartFile("pdf", "../nota-firmada.pdf", "text/plain", content));

        assertThat(stored.nombre()).isEqualTo("nota-firmada.pdf");
        assertThat(stored.tamanoBytes()).isEqualTo(content.length);
        assertThat(Files.readAllBytes(Path.of(stored.ruta()))).isEqualTo(content);

        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(Path.of(stored.ruta())).exists();
    }

    @Test
    void removesNewPdfIfTheTransactionRollsBack() {
        NotaPdfStorageService.StoredPdf stored = service.guardar(
            new MockMultipartFile("pdf", "nota.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)));

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(Path.of(stored.ruta())).doesNotExist();
    }

    @Test
    void rejectsContentThatIsNotAPdf() {
        assertThatThrownBy(() -> service.guardar(
            new MockMultipartFile("pdf", "nota.pdf", "application/pdf", "texto plano".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PDF");
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
