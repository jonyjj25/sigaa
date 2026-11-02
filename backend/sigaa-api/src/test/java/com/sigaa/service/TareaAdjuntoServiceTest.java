package com.sigaa.service;

import com.sigaa.model.TareaAdjunto;
import com.sigaa.repository.TareaAdjuntoRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TareaAdjuntoServiceTest {
    @TempDir Path directory;
    TareaAdjuntoRepository repository;
    TareaAdjuntoService service;
    final byte[] pdf = "%PDF-1.4\ncontenido de prueba".getBytes(StandardCharsets.UTF_8);

    @BeforeEach void setUp() {
        repository = mock(TareaAdjuntoRepository.class);
        service = new TareaAdjuntoService(repository, directory);
        when(repository.findByTareaIdOrderById(1L)).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach void tearDown() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void storesRealBytesWithVerifiedMimeAndSafeName() throws Exception {
        service.sincronizar(1L, List.of(Map.of("archivoIndice", 0)), List.of(file()));
        TareaAdjunto adjunto = saved();
        assertThat(adjunto.getNombre()).isEqualTo("asistencias.pdf");
        assertThat(adjunto.getTipoContenido()).isEqualTo("application/pdf");
        assertThat(adjunto.getTamanoBytes()).isEqualTo(pdf.length);
        assertThat(Files.readAllBytes(service.archivo(adjunto))).isEqualTo(pdf);
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(service.archivo(adjunto)).exists();
    }

    @Test void retainsExistingFileByIdWhenTaskIsEdited() throws Exception {
        TareaAdjunto old = existing();
        service.sincronizar(1L, List.of(Map.of("id", "7", "rutaArchivo", "outside", "nombre", "changed")), List.of());
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(service.archivo(old)).exists();
        assertThat(old.getNombre()).isEqualTo("original.pdf");
    }

    @Test void availabilityTracksMissingFilesAndRejectsPathsOutsideStorage() throws Exception {
        TareaAdjunto attachment = existing();
        assertThat(service.disponible(attachment)).isTrue();
        Files.delete(service.archivo(attachment));
        assertThat(service.disponible(attachment)).isFalse();
        attachment.setRutaArchivo(directory.resolve("../other.pdf").toString());
        assertThat(service.disponible(attachment)).isFalse();
        attachment.setRutaArchivo(null);
        assertThat(service.disponible(attachment)).isFalse();
    }

    @Test void rejectsAttachmentIdFromAnotherTask() {
        assertThatThrownBy(() -> service.sincronizar(1L, List.of(Map.of("id", 99)), List.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no pertenece");
        verify(repository, never()).delete(any());
    }

    @Test void rejectsPdfWithInvalidContentAndEmptyFile() {
        for (byte[] content : List.of("not a pdf".getBytes(StandardCharsets.UTF_8), new byte[0])) {
            assertThatThrownBy(() -> service.sincronizar(1L, List.of(Map.of("archivoIndice", 0)),
                List.of(new MockMultipartFile("archivos", "bad.pdf", "application/pdf", content))))
                .isInstanceOf(IllegalArgumentException.class);
        }
        verify(repository, never()).save(any());
    }

    @Test void rejectsOversizedFileBeforeReadingItsContent() {
        var large = mock(org.springframework.web.multipart.MultipartFile.class);
        when(large.getSize()).thenReturn(11L * 1024 * 1024);
        assertThatThrownBy(() -> service.sincronizar(1L, List.of(Map.of("archivoIndice", 0)), List.of(large)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 MB");
        verify(repository, never()).save(any());
    }

    @Test void removesNewFileWhenTransactionRollsBackButKeepsOldFile() throws Exception {
        TareaAdjunto old = existing();
        service.sincronizar(1L, List.of(Map.of("archivoIndice", 0)), List.of(file()));
        Path newPath = service.archivo(saved());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(newPath).doesNotExist();
        assertThat(service.archivo(old)).exists();
    }

    @Test void removesReplacedFileOnlyAfterSuccessfulCommit() throws Exception {
        TareaAdjunto old = existing();
        Path oldPath = service.archivo(old);
        service.sincronizar(1L, List.of(Map.of("archivoIndice", 0)), List.of(file()));
        Path newPath = service.archivo(saved());
        assertThat(oldPath).exists();
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(oldPath).doesNotExist();
        assertThat(newPath).exists();
    }

    @Test void cleansUpAlreadyStoredFilesWhenLaterFileFailsValidation() throws Exception {
        assertThatThrownBy(() -> service.sincronizar(1L, List.of(Map.of("archivoIndice", 0), Map.of("archivoIndice", 1)),
            List.of(file(), new MockMultipartFile("archivos", "bad.pdf", "application/pdf", new byte[0]))))
            .isInstanceOf(IllegalArgumentException.class);
        try (var paths = Files.list(directory)) { assertThat(paths.count()).isZero(); }
    }

    @Test void referencesHaveNoDownloadAndPathsCannotEscapeStorage() {
        TareaAdjunto reference = new TareaAdjunto();
        assertThatThrownBy(() -> service.archivo(reference)).isInstanceOf(NoSuchElementException.class);
        reference.setRutaArchivo(directory.resolve("../outside.pdf").toString());
        assertThatThrownBy(() -> service.archivo(reference)).isInstanceOf(IllegalArgumentException.class);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("archivos", "../asistencias.pdf", "text/html", pdf);
    }

    private TareaAdjunto saved() {
        var captor = ArgumentCaptor.forClass(TareaAdjunto.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private TareaAdjunto existing() throws Exception {
        TareaAdjunto old = new TareaAdjunto();
        old.setId(7L); old.setTareaId(1L); old.setNombre("original.pdf");
        old.setRutaArchivo(Files.write(directory.resolve("original.pdf"), pdf).toString());
        when(repository.findByTareaIdOrderById(1L)).thenReturn(List.of(old));
        return old;
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status));
    }
}
