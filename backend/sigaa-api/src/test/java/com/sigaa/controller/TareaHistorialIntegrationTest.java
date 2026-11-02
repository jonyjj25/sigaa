package com.sigaa.controller;

import com.sigaa.model.AuditLog;
import com.sigaa.model.Tarea;
import com.sigaa.model.TareaChecklist;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.TareaChecklistRepository;
import com.sigaa.repository.TareaRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.InternalNotificationService;
import com.sigaa.service.UserNotificationEmailService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Transacciones confirmadas sobre PostgreSQL efímero; solo cuentas y tareas ficticias. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "actor@example.test", authorities = {
    "ROLE_superadmin", "Tareas:Ver", "Tareas:Editar", "Tareas:Asignar", "Tareas:Completar"})
class TareaHistorialIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TareaRepository tareas;
    @Autowired TareaChecklistRepository checklist;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;
    @MockitoSpyBean AuditLogRepository audit;
    @MockitoBean InternalNotificationService notifications;
    @MockitoBean UserNotificationEmailService emails;
    Tarea tarea;
    Usuario anterior, nuevo, ajeno;

    @BeforeEach void preparar() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            anterior = cuenta("Anterior"); nuevo = cuenta("Nuevo"); ajeno = cuenta("Ajeno");
            tarea = new Tarea(); tarea.setTitulo("Tarea ficticia de historial");
            tarea.setCodigo("TEST-HIST-" + UUID.randomUUID()); tarea.setFechaCreacion(LocalDate.now());
            tarea.setAsignadoUsuario(anterior); tarea.setAsignado("Anterior Prueba");
            tarea = tareas.saveAndFlush(tarea);
        });
    }

    @AfterEach void limpiar() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.deleteAll(movimientos());
            // Prueba de aislamiento: esta fila ficticia tiene el mismo ID y otra entidad.
            audit.deleteAll(audit.findByEntidadAndEntidadIdOrderByFechaDesc("otra_entidad_prueba", tarea.getId()));
            tareas.deleteById(tarea.getId()); tareas.flush();
            usuarios.deleteAllById(List.of(anterior.getId(), nuevo.getId(), ajeno.getId()));
        });
    }

    @Test void defaultProjectUsesOnlyTheExistingAuthorizedModuleCatalog() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                mvc.perform(post("/api/tareas").with(user(anterior.getCorreo()).authorities(
                        new SimpleGrantedAuthority("ROLE_superadmin"), new SimpleGrantedAuthority("Tareas:Crear")))
                        .contentType("application/json").content("{\"titulo\":\"Tarea ficticia sin selector\",\"tipo\":\"\",\"proyecto\":\"\"}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.proyecto").isNotEmpty());
                mvc.perform(post("/api/tareas").with(user(ajeno.getCorreo()).authorities(
                        new SimpleGrantedAuthority("ROLE_miembro"), new SimpleGrantedAuthority("Tareas:Crear")))
                        .contentType("application/json").content("{\"titulo\":\"Sin módulo autorizado\",\"tipo\":\"\",\"proyecto\":\"\"}"))
                        .andExpect(status().isForbidden());
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { tx.setRollbackOnly(); }
        });
    }

    @Test void updateRecordsEachRealChangeWithAuthenticatedAuthorTimeAndPreviousAndNewValues() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        mvc.perform(put("/api/tareas/{id}", tarea.getId()).contentType("application/json").content(cambios()))
            .andExpect(status().isOk());
        assertThat(movimientos()).hasSize(3).allSatisfy(log -> {
            assertThat(log.getUsuario()).isEqualTo("actor@example.test");
            assertThat(log.getFecha()).isBetween(before, LocalDateTime.now());
            assertThat(log.getEntidadId()).isEqualTo(tarea.getId());
        });
        assertThat(movimiento("CAMBIO_RESPONSABLE").getDetalle()).contains("Anterior Prueba", "Nuevo Prueba",
            "ID: " + anterior.getId(), "ID: " + nuevo.getId());
        assertThat(movimiento("CAMBIO_ESTADO").getDetalle()).contains("Por hacer", "En progreso");
        assertThat(movimiento("CAMBIO_AVANCE").getDetalle()).contains("0%", "40%");
        var guardada = tareas.findById(tarea.getId()).orElseThrow();
        assertThat(guardada.getAsignadoUsuario().getId()).isEqualTo(nuevo.getId());
        assertThat(guardada.getEstado()).isEqualTo("en_progreso");
        assertThat(guardada.getProgreso()).isEqualTo(40);
    }

    @Test void multipartUpdateUsesTheSameHistoryAndIgnoresClientSuppliedAuthorAndHistory() throws Exception {
        String body = cambios().replace("}", ",\"usuario\":\"suplantado@example.test\",\"historial\":[{\"accion\":\"FALSO\"}]}");
        mvc.perform(multipart(HttpMethod.PUT, "/api/tareas/{id}", tarea.getId())
            .file(new MockMultipartFile("datos", "datos.json", "application/json", body.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
            .andExpect(status().isOk());
        assertThat(movimientos()).hasSize(3).allSatisfy(log -> assertThat(log.getUsuario()).isEqualTo("actor@example.test"));
    }

    @Test void repeatedUpdatesAndUnrelatedChangesDoNotDuplicateOrOverwriteHistory() throws Exception {
        mvc.perform(put("/api/tareas/{id}", tarea.getId()).contentType("application/json").content(cambios())).andExpect(status().isOk());
        var ids = movimientos().stream().map(AuditLog::getId).toList();
        mvc.perform(put("/api/tareas/{id}", tarea.getId()).contentType("application/json").content(cambios())).andExpect(status().isOk());
        mvc.perform(put("/api/tareas/{id}", tarea.getId()).contentType("application/json").content("{\"titulo\":\"Otro título ficticio\"}"))
            .andExpect(status().isOk());
        assertThat(movimientos().stream().map(AuditLog::getId)).containsExactlyElementsOf(ids);
    }

    @Test void concurrentIdenticalUpdatesCannotCommitDuplicateMovements() throws Exception {
        var bothReadPreviousState = new CyclicBarrier(2);
        doAnswer(invocation -> {
            AuditLog entry = invocation.getArgument(0);
            bothReadPreviousState.await(15, TimeUnit.SECONDS);
            entityManager.persist(entry);
            return entry;
        }).when(audit).save(any(AuditLog.class));
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> update = () -> mvc.perform(patch("/api/tareas/{id}/estado", tarea.getId())
                .with(user("actor@example.test").authorities(new SimpleGrantedAuthority("ROLE_superadmin"),
                    new SimpleGrantedAuthority("Tareas:Completar")))
                .contentType("application/json").content("{\"estado\":\"en_progreso\"}"))
                .andReturn().getResponse().getStatus();
            var first = pool.submit(update); var second = pool.submit(update);
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(movimientos()).singleElement().satisfies(log -> assertThat(log.getAccion()).isEqualTo("CAMBIO_ESTADO"));
    }

    @Test void assignmentEndpointRecordsChangesButDoesNotTreatANameRefreshAsAnotherResponsible() throws Exception {
        asignar(nuevo.getId()); asignar(nuevo.getId());
        assertThat(movimientos()).hasSize(1);
        nuevo.setNombre("Nombre actualizado"); usuarios.saveAndFlush(nuevo);
        asignar(nuevo.getId());
        assertThat(movimientos()).hasSize(1);
    }

    @Test void differentUsersWithTheSameDisplayNameAreStillRecorded() throws Exception {
        nuevo.setNombre(anterior.getNombre()); usuarios.saveAndFlush(nuevo);
        asignar(nuevo.getId());
        assertThat(movimientos()).singleElement().satisfies(log -> assertThat(log.getDetalle())
            .contains("ID: " + anterior.getId(), "ID: " + nuevo.getId()));
    }

    @Test void removingTheResponsibleAndChangingLegacyTextAreRecorded() throws Exception {
        mvc.perform(put("/api/tareas/{id}", tarea.getId()).contentType("application/json")
            .content("{\"asignadoUsuarioId\":null,\"asignado\":\"Referencia anterior\"}")).andExpect(status().isOk());
        assertThat(movimientos()).hasSize(1);
        mvc.perform(put("/api/tareas/{id}", tarea.getId()).contentType("application/json")
            .content("{\"asignado\":\"Referencia nueva\"}")).andExpect(status().isOk());
        assertThat(movimientos()).hasSize(2);
        assertThat(movimientos().getFirst().getDetalle()).contains("Referencia nueva");
    }

    @Test void stateEndpointRecordsStateAndProgressOnlyWhenTheyActuallyChange() throws Exception {
        estado("completada", 100); estado("completada", 100);
        assertThat(movimientos()).hasSize(2);
        estado("completada", 75);
        assertThat(movimientos()).hasSize(3);
        assertThat(movimientos().stream().filter(log -> "CAMBIO_AVANCE".equals(log.getAccion()))).hasSize(2);
    }

    @Test void checklistRecordsComputedProgressAndAutomaticStateChangesIncludingReopening() throws Exception {
        TareaChecklist first = item(false), second = item(false);
        marcar(first); marcar(second);
        assertThat(movimientos()).hasSize(3);
        assertThat(movimiento("CAMBIO_ESTADO").getDetalle()).contains("Por hacer", "Completada");
        marcar(first);
        assertThat(movimientos()).hasSize(5);
        assertThat(tareas.findById(tarea.getId()).orElseThrow().getEstado()).isEqualTo("en_progreso");
    }

    @Test void roundingThatLeavesPercentageUnchangedDoesNotCreateAMovement() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (int i = 0; i < 101; i++) item(false);
        });
        marcar(checklist.findByTareaIdOrderById(tarea.getId()).getFirst());
        assertThat(movimientos()).isEmpty();
    }

    @Test void historyKeepsPreviousEntriesInChronologicalOrderAndNeverReturnsOtherEntityOrIp() throws Exception {
        AuditLog previous = log("ANTERIOR", "Movimiento previo");
        previous.setDireccionIp("192.0.2.1"); audit.saveAndFlush(previous);
        AuditLog other = log("AJENO", "No debe aparecer"); other.setEntidad("otra_entidad_prueba"); audit.saveAndFlush(other);
        estado("en_progreso", 50);
        mvc.perform(get("/api/tareas/{id}/historial", tarea.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].id").value(previous.getId().toString()))
            .andExpect(jsonPath("$[0].accion").value("ANTERIOR"))
            .andExpect(jsonPath("$[1].accion").value("CAMBIO_ESTADO"))
            .andExpect(jsonPath("$[2].accion").value("CAMBIO_AVANCE"))
            .andExpect(jsonPath("$[0].direccionIp").doesNotExist());
    }

    @Test void taskOwnerWithReadPermissionCanReadHistory() throws Exception {
        estado("en_progreso", 25);
        mvc.perform(get("/api/tareas/{id}/historial", tarea.getId()).with(user(anterior.getCorreo()).authorities(new SimpleGrantedAuthority("Tareas:Ver"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void readPermissionWithoutResourceAccessDoesNotRevealHistory() throws Exception {
        mvc.perform(get("/api/tareas/{id}/historial", tarea.getId()).with(user(ajeno.getCorreo()).authorities(new SimpleGrantedAuthority("Tareas:Ver"))))
            .andExpect(status().isNotFound());
    }

    @Test void accessWithoutFunctionalReadPermissionIsForbidden() throws Exception {
        mvc.perform(get("/api/tareas/{id}/historial", tarea.getId()).with(user(anterior.getCorreo()).authorities(new SimpleGrantedAuthority("Tareas:Completar"))))
            .andExpect(status().isForbidden());
    }

    @Test void anonymousCannotReadOrWriteHistory() throws Exception {
        mvc.perform(get("/api/tareas/{id}/historial", tarea.getId()).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/tareas/{id}/estado", tarea.getId()).with(anonymous()).contentType("application/json")
            .content("{\"estado\":\"completada\",\"progreso\":100}")).andExpect(status().isUnauthorized());
        assertThat(movimientos()).isEmpty();
    }

    @Test void forbiddenEditDoesNotChangeTaskOrHistory() throws Exception {
        mvc.perform(put("/api/tareas/{id}", tarea.getId()).with(user(ajeno.getCorreo()).authorities(new SimpleGrantedAuthority("Tareas:Editar")))
            .contentType("application/json").content(cambios())).andExpect(status().isForbidden());
        assertThat(movimientos()).isEmpty();
        assertThat(tareas.findById(tarea.getId()).orElseThrow().getProgreso()).isZero();
    }

    @Test void missingTaskHasNoHistory() throws Exception {
        mvc.perform(get("/api/tareas/{id}/historial", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"actualizar", "asignar", "estado", "checklist"})
    void auditFailureRollsBackTheTaskChecklistAndAnyAlreadyInsertedMovements(String operation) throws Exception {
        TareaChecklist item = item(false);
        AtomicInteger writes = new AtomicInteger();
        doAnswer(invocation -> {
            AuditLog entry = invocation.getArgument(0);
            entityManager.persist(entry); entityManager.flush();
            if (writes.incrementAndGet() >= ("asignar".equals(operation) ? 1 : 2)) {
                throw new IllegalStateException("Fallo ficticio de auditoría tras persistir");
            }
            return entry;
        }).when(audit).save(any(AuditLog.class));
        switch (operation) {
            case "actualizar" -> mvc.perform(put("/api/tareas/{id}", tarea.getId()).contentType("application/json").content(cambios()))
                .andExpect(status().isConflict());
            case "asignar" -> mvc.perform(patch("/api/tareas/{id}/asignacion", tarea.getId()).contentType("application/json")
                .content("{\"asignadoUsuarioId\":" + nuevo.getId() + "}")).andExpect(status().isConflict());
            case "estado" -> mvc.perform(patch("/api/tareas/{id}/estado", tarea.getId()).contentType("application/json")
                .content("{\"estado\":\"completada\",\"progreso\":100}")).andExpect(status().isConflict());
            case "checklist" -> mvc.perform(patch("/api/tareas/{id}/checklist/{checkId}", tarea.getId(), item.getId()))
                .andExpect(status().isConflict());
            default -> throw new IllegalArgumentException(operation);
        }
        assertThat(writes.get()).isPositive();
        assertThat(movimientos()).isEmpty();
        Tarea original = tareas.findById(tarea.getId()).orElseThrow();
        assertThat(original.getAsignadoUsuario().getId()).isEqualTo(anterior.getId());
        assertThat(original.getEstado()).isEqualTo("por_hacer"); assertThat(original.getProgreso()).isZero();
        assertThat(checklist.findById(item.getId()).orElseThrow().getCompletado()).isFalse();
    }

    private Usuario cuenta(String nombre) {
        String key = UUID.randomUUID().toString();
        var usuario = new Usuario(); usuario.setNombre(nombre); usuario.setApellido("Prueba");
        usuario.setCorreo(key + "@example.test"); usuario.setUsername("test-" + key);
        usuario.setPassword("TEST_ONLY_NOT_REAL"); usuario.setActivo(true); usuario.setBloqueado(false);
        return usuarios.saveAndFlush(usuario);
    }

    private String cambios() {
        return "{\"asignadoUsuarioId\":" + nuevo.getId() + ",\"estado\":\"en_progreso\",\"progreso\":40}";
    }

    private List<AuditLog> movimientos() { return audit.findByEntidadAndEntidadIdOrderByFechaDesc("tarea", tarea.getId()); }
    private AuditLog movimiento(String type) { return movimientos().stream().filter(log -> type.equals(log.getAccion())).findFirst().orElseThrow(); }

    private AuditLog log(String action, String detail) {
        var log = new AuditLog(); log.setUsuario("previo@example.test"); log.setEntidad("tarea"); log.setEntidadId(tarea.getId());
        log.setAccion(action); log.setDetalle(detail); return log;
    }

    private TareaChecklist item(boolean completed) {
        var item = new TareaChecklist(); item.setTareaId(tarea.getId()); item.setTexto("Paso ficticio"); item.setCompletado(completed);
        return checklist.saveAndFlush(item);
    }

    private void asignar(Long id) throws Exception {
        mvc.perform(patch("/api/tareas/{id}/asignacion", tarea.getId()).contentType("application/json")
            .content("{\"asignadoUsuarioId\":" + id + "}")).andExpect(status().isOk());
    }

    private void estado(String state, int progress) throws Exception {
        mvc.perform(patch("/api/tareas/{id}/estado", tarea.getId()).contentType("application/json")
            .content("{\"estado\":\"" + state + "\",\"progreso\":" + progress + "}")).andExpect(status().isOk());
    }

    private void marcar(TareaChecklist item) throws Exception {
        mvc.perform(patch("/api/tareas/{id}/checklist/{checkId}", tarea.getId(), item.getId())).andExpect(status().isOk());
    }
}
