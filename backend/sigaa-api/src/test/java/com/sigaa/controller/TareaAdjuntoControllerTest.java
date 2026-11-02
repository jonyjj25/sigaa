package com.sigaa.controller;

import com.sigaa.config.GlobalExceptionHandler;
import com.sigaa.model.Tarea;
import com.sigaa.model.TareaAdjunto;
import com.sigaa.model.AuditLog;
import com.sigaa.repository.*;
import com.sigaa.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TareaAdjuntoControllerTest {
    @TempDir Path directory;
    TareaRepository tareas;
    TareaChecklistRepository checklists;
    TareaAdjuntoRepository adjuntos;
    TareaAccessService access;
    TareaAdjuntoService storage;
    AuditLogRepository audit;
    MockMvc mvc;
    Tarea tarea;

    @BeforeEach void setUp() {
        tareas = mock(TareaRepository.class); checklists = mock(TareaChecklistRepository.class);
        adjuntos = mock(TareaAdjuntoRepository.class);
        access = mock(TareaAccessService.class); storage = mock(TareaAdjuntoService.class);
        audit = mock(AuditLogRepository.class);
        tarea = new Tarea(); tarea.setId(1L);
        when(tareas.findById(1L)).thenReturn(Optional.of(tarea));
        var controller = new TareaController(tareas, checklists, adjuntos,
            mock(UsuarioRepository.class), mock(ModuloRepository.class), mock(UserNotificationEmailService.class),
            mock(InternalNotificationService.class), access, storage, audit, mock(TareaVencimientoService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void multipartEditBindsMetadataAndActualFile() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/tareas/1")
            .file(new MockMultipartFile("datos", "datos.json", "application/json",
                "{\"titulo\":\"Editada\",\"adjuntos\":[{\"archivoIndice\":0}]}".getBytes()))
            .file(new MockMultipartFile("archivos", "test.pdf", "application/pdf", "%PDF-1.4".getBytes())))
            .andExpect(status().isOk());
        verify(access).exigirGestion(tarea);
        verify(storage).sincronizar(eq(1L), argThat(items -> items.size() == 1 && items.getFirst().containsKey("archivoIndice")),
            argThat(files -> files.size() == 1 && files.getFirst().getSize() == 8));
    }

    @Test void forbiddenEditDoesNotStoreFiles() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(access).exigirGestion(tarea);
        mvc.perform(multipart(HttpMethod.PUT, "/api/tareas/1")
            .file(new MockMultipartFile("datos", "datos.json", "application/json", "{\"adjuntos\":[]}".getBytes())))
            .andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test void editRejectsFinishDateBeforeStartDate() throws Exception {
        mvc.perform(put("/api/tareas/1").contentType("application/json")
            .content("{\"fechaCreacion\":\"2026-10-20\",\"fechaLimite\":\"2026-10-19\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La fecha de finalización no puede ser anterior a la fecha de inicio."));
        verify(tareas, never()).save(any());
        verifyNoInteractions(storage);
    }

    @Test void editPreservesCompletedStateAndChecklistValues() throws Exception {
        tarea.setEstado("completada");
        tarea.setFechaCreacion(java.time.LocalDate.of(2026, 10, 20));
        mvc.perform(put("/api/tareas/1").contentType("application/json")
            .content("{\"fechaLimite\":\"2026-10-31\",\"checklist\":[{\"texto\":\"Hecho\",\"completado\":true}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("completada"));
        verify(checklists).save(argThat(item -> Boolean.TRUE.equals(item.getCompletado())));
    }

    @Test void editDoesNotEraseTheStartDateWhenAnEmptyValueIsSent() throws Exception {
        tarea.setFechaCreacion(java.time.LocalDate.of(2026, 10, 20));

        mvc.perform(put("/api/tareas/1").contentType("application/json")
            .content("{\"fechaCreacion\":\"\",\"fechaLimite\":\"2026-10-31\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fechaCreacion").value("2026-10-20"));

        org.junit.jupiter.api.Assertions.assertEquals(
            java.time.LocalDate.of(2026, 10, 20), tarea.getFechaCreacion());
    }

    @Test void downloadReturnsExactBytesAndSafeHeaders() throws Exception {
        var adjunto = attachment(1L);
        byte[] bytes = "%PDF-1.4\nhello".getBytes();
        when(storage.archivo(adjunto)).thenReturn(Files.write(directory.resolve("test.pdf"), bytes));
        mvc.perform(get("/api/tareas/1/adjuntos/7/descargar"))
            .andExpect(status().isOk()).andExpect(content().bytes(bytes))
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(access).exigirLectura(tarea);
    }

    @Test void downloadRejectsAttachmentFromAnotherTask() throws Exception {
        attachment(2L);
        mvc.perform(get("/api/tareas/1/adjuntos/7/descargar")).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test void downloadRejectsUnrelatedUserBeforeLookingUpFile() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(access).exigirLectura(tarea);
        mvc.perform(get("/api/tareas/1/adjuntos/7/descargar")).andExpect(status().isForbidden());
        verifyNoInteractions(adjuntos, storage);
    }

    @Test void historyOrdersByTimestampThenIdAndDoesNotExposeTheAuditIp() throws Exception {
        when(access.puedeVer(tarea)).thenReturn(true);
        var first = movement(1L, LocalDateTime.of(2026, 1, 1, 9, 0));
        var second = movement(2L, first.getFecha());
        var third = movement(3L, first.getFecha().plusMinutes(1));
        when(audit.findByEntidadAndEntidadIdOrderByFechaDesc("tarea", 1L)).thenReturn(List.of(third, second, first));
        mvc.perform(get("/api/tareas/1/historial")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("1")).andExpect(jsonPath("$[1].id").value("2"))
            .andExpect(jsonPath("$[2].id").value("3")).andExpect(jsonPath("$[0].direccionIp").doesNotExist());
    }

    private AuditLog movement(Long id, LocalDateTime date) {
        var log = new AuditLog(); log.setId(id); log.setUsuario("test@example.test");
        log.setAccion("CAMBIO_ESTADO"); log.setDetalle("Estado anterior: Por hacer. Nuevo estado: En progreso.");
        log.setDireccionIp("192.0.2.1"); ReflectionTestUtils.setField(log, "fecha", date); return log;
    }

    private TareaAdjunto attachment(Long taskId) {
        TareaAdjunto a = new TareaAdjunto(); a.setId(7L); a.setTareaId(taskId);
        a.setNombre("test.pdf"); a.setTipoContenido("application/pdf");
        when(adjuntos.findById(7L)).thenReturn(Optional.of(a));
        return a;
    }
}
