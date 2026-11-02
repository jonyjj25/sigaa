package com.sigaa.controller;

import com.sigaa.model.AuditLog;
import com.sigaa.model.Reporte;
import com.sigaa.model.ReporteProgramacion;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.ReporteRepository;
import com.sigaa.repository.ReporteProgramacionRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportesArchivosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ReporteRepository reportes;
    @Autowired ReporteProgramacionRepository programaciones;
    @Autowired ReporteController controller;
    @Autowired ReporteProgramacionController programacionController;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditLogRepository audit;
    @TempDir Path directory;
    private final List<Long> idsReportes = new ArrayList<>();
    private final List<Long> idsProgramaciones = new ArrayList<>();
    private final String usuario = "reporte-test-" + UUID.randomUUID() + "@example.test";
    private Path previousReportDir;
    private Path previousScheduleDir;
    private Path original;
    private Reporte reporte;

    @BeforeEach void preparar() throws IOException {
        previousReportDir = (Path) ReflectionTestUtils.getField(controller, "uploadDir");
        previousScheduleDir = (Path) ReflectionTestUtils.getField(programacionController, "uploadDir");
        ReflectionTestUtils.setField(controller, "uploadDir", directory);
        ReflectionTestUtils.setField(programacionController, "uploadDir", directory);
        original = Files.writeString(directory.resolve("original.txt"), "contenido anterior");
        reporte = crearReporte(original.toString());
    }

    @AfterEach void limpiar() {
        reset(audit);
        ReflectionTestUtils.setField(controller, "uploadDir", previousReportDir);
        ReflectionTestUtils.setField(programacionController, "uploadDir", previousScheduleDir);
        idsReportes.forEach(id -> jdbc.update("DELETE FROM reportes WHERE id = ?", id));
        idsProgramaciones.forEach(id -> jdbc.update("DELETE FROM reporte_programaciones WHERE id = ?", id));
        jdbc.update("DELETE FROM audit_logs WHERE usuario = ?", usuario);
    }

    @Test void commitConservaLosNuevosYLosAnteriores() throws Exception {
        subir(reporte.getId(), archivo("nuevo.txt"), archivo("otro.txt"));
        String rutas = reportes.findById(reporte.getId()).orElseThrow().getArchivosRuta();
        assertThat(rutas.split(";")).hasSize(3).contains(original.toString());
        for (String ruta : rutas.split(";")) assertThat(Path.of(ruta)).exists();
        assertThat(Files.readString(Path.of(rutas.split(";")[1]))).isEqualTo("contenido sintético");
        assertThat(audit.findByEntidadAndEntidadIdOrderByFechaDesc("reporte", reporte.getId()))
                .extracting(AuditLog::getAccion).containsExactly("SUBIR_ARCHIVO");
    }

    @Test void falloDeAuditoriaRevierteDatosYCopiaNueva() throws Exception {
        fallarAuditoriaDespuesDeFlush();
        mvc.perform(upload(reporte.getId()).file(archivo("nuevo.txt")).file(archivo("otro.txt")))
                .andExpect(status().isConflict());
        comprobarSoloOriginal();
        assertThat(audit.findByEntidadAndEntidadIdOrderByFechaDesc("reporte", reporte.getId())).isEmpty();
    }

    @Test void falloRealDeBaseDeDatosAlConfirmarLimpiaTodasLasCopias() throws Exception {
        var request = upload(reporte.getId());
        for (int i = 0; i < 15; i++) request.file(archivo("nombre-largo-" + "x".repeat(100) + i + ".txt"));
        // El listado supera varchar(2000): el fallo se produce al escribir/confirmar en PostgreSQL.
        mvc.perform(request).andExpect(status().isConflict());
        comprobarSoloOriginal();
    }

    @Test void eliminarConRollbackConservaRegistroYArchivo() throws Exception {
        fallarAuditoriaDespuesDeFlush();
        mvc.perform(delete("/api/reportes/{id}", reporte.getId()).with(user(usuario)
                .authorities(new SimpleGrantedAuthority("Reporte:Eliminar")))).andExpect(status().isConflict());
        comprobarSoloOriginal();
    }

    @Test void eliminarConfirmadoLimpiaElUltimoArchivoSinReferencias() throws Exception {
        eliminarReporte(reporte.getId());
        assertThat(reportes.findById(reporte.getId())).isEmpty();
        assertThat(original).doesNotExist();
    }

    @Test void eliminarNoBorraAdjuntosCompartidosConProgramacionesOInstantaneas() throws Exception {
        Reporte otraInstantanea = crearReporte(original.toString());
        ReporteProgramacion programacion = crearProgramacion(original.toString());
        eliminarReporte(reporte.getId());
        assertThat(original).exists();
        eliminarProgramacion(programacion.getId());
        assertThat(original).exists();
        eliminarReporte(otraInstantanea.getId());
        assertThat(original).doesNotExist();
    }

    @Test void programacionConCommitConservaLosAdjuntosNuevosYAnteriores() throws Exception {
        ReporteProgramacion programacion = crearProgramacion(original.toString());
        mvc.perform(uploadProgramacion(programacion.getId()).file(archivo("nuevo.txt"))).andExpect(status().isOk());
        String rutas = programaciones.findById(programacion.getId()).orElseThrow().getArchivosRuta();
        assertThat(rutas.split(";")).hasSize(2).contains(original.toString());
        for (String ruta : rutas.split(";")) assertThat(Path.of(ruta)).exists();
    }

    @Test void programacionRechazaUnLoteInvalidoSinDejarElPrimerArchivo() throws Exception {
        ReporteProgramacion programacion = crearProgramacion(original.toString());
        mvc.perform(uploadProgramacion(programacion.getId()).file(archivo("valido.txt"))
                .file(new MockMultipartFile("files", "invalido.pdf", "application/pdf", "no es PDF".getBytes())))
                .andExpect(status().isBadRequest());
        comprobarProgramacionSinCambios(programacion);
    }

    @Test void programacionConFalloDeCopiaRevierteArchivosYaAlmacenados() throws Exception {
        ReporteProgramacion programacion = crearProgramacion(original.toString());
        MockMultipartFile fallaAlCopiar = new MockMultipartFile("files", "segundo.txt", "text/plain", "válido".getBytes()) {
            private int lecturas;
            @Override public InputStream getInputStream() throws IOException {
                if (++lecturas == 2) throw new IOException("Fallo sintético de almacenamiento");
                return super.getInputStream();
            }
        };
        mvc.perform(uploadProgramacion(programacion.getId()).file(archivo("primero.txt")).file(fallaAlCopiar))
                .andExpect(status().isBadRequest());
        comprobarProgramacionSinCambios(programacion);
    }

    @Test void programacionConFalloDeAuditoriaLimpiaCopiasTrasRollback() throws Exception {
        ReporteProgramacion programacion = crearProgramacion(original.toString());
        fallarAuditoriaDespuesDeFlush();
        mvc.perform(uploadProgramacion(programacion.getId()).file(archivo("nuevo.txt"))).andExpect(status().isConflict());
        comprobarProgramacionSinCambios(programacion);
    }

    @Test void administradorRetiradoNoPuedeCambiarNiEliminarProgramaciones() throws Exception {
        ReporteProgramacion programacion = crearProgramacion(original.toString());
        mvc.perform(multipart("/api/reportes/programaciones/{id}/archivos", programacion.getId())
                .file(archivo("rechazado.txt"))
                .with(user(usuario).authorities(new SimpleGrantedAuthority("ROLE_administrador"),
                        new SimpleGrantedAuthority("Reporte:Configurar"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/reportes/programaciones/{id}", programacion.getId())
                .with(user(usuario).authorities(new SimpleGrantedAuthority("ROLE_administrador"),
                        new SimpleGrantedAuthority("Reporte:Configurar"))))
                .andExpect(status().isForbidden());
        comprobarProgramacionSinCambios(programacion);
    }

    private void fallarAuditoriaDespuesDeFlush() {
        doAnswer(invocation -> {
            entityManager.flush();
            throw new IllegalStateException("Fallo sintético de auditoría después del flush");
        }).when(audit).save(any(AuditLog.class));
    }

    private void comprobarSoloOriginal() throws IOException {
        assertThat(reportes.findById(reporte.getId()).orElseThrow().getArchivosRuta()).isEqualTo(original.toString());
        try (var paths = Files.list(directory)) { assertThat(paths.toList()).containsExactly(original); }
        assertThat(Files.readString(original)).isEqualTo("contenido anterior");
    }

    private void comprobarProgramacionSinCambios(ReporteProgramacion programacion) throws IOException {
        assertThat(programaciones.findById(programacion.getId()).orElseThrow().getArchivosRuta()).isEqualTo(original.toString());
        comprobarSoloOriginal();
    }

    private Reporte crearReporte(String rutas) {
        Reporte r = new Reporte(); r.setNombre("Reporte sintético"); r.setModulo("Tareas");
        r.setFecha(LocalDate.of(2026, 10, 5)); r.setGeneradoPor(usuario); r.setArchivosRuta(rutas);
        r = reportes.saveAndFlush(r); idsReportes.add(r.getId()); return r;
    }

    private ReporteProgramacion crearProgramacion(String rutas) {
        ReporteProgramacion p = new ReporteProgramacion(); p.setNombre("Programación sintética"); p.setModulo("Tareas");
        p.setPeriodicidad("semanal"); p.setFechaInicio(LocalDate.of(2099, 1, 1)); p.setHora(LocalTime.NOON);
        p.setProximaEjecucion(LocalDateTime.of(2099, 1, 1, 12, 0)); p.setCreadoPor(usuario); p.setArchivosRuta(rutas);
        p = programaciones.saveAndFlush(p); idsProgramaciones.add(p.getId()); return p;
    }

    private MockMultipartFile archivo(String nombre) {
        return new MockMultipartFile("files", nombre, "text/plain", "contenido sintético".getBytes());
    }

    private MockMultipartHttpServletRequestBuilder upload(Long id) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/reportes/{id}/archivos", id)
                .with(user(usuario).authorities(new SimpleGrantedAuthority("Reporte:Subir archivos")));
    }

    private MockMultipartHttpServletRequestBuilder uploadProgramacion(Long id) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/reportes/programaciones/{id}/archivos", id)
                .with(user(usuario).roles("superadmin"));
    }

    private void subir(Long id, MockMultipartFile... files) throws Exception {
        var request = upload(id); for (var file : files) request.file(file);
        mvc.perform(request).andExpect(status().isOk());
    }

    private void eliminarReporte(Long id) throws Exception {
        mvc.perform(delete("/api/reportes/{id}", id).with(user(usuario)
                .authorities(new SimpleGrantedAuthority("Reporte:Eliminar")))).andExpect(status().isNoContent());
    }

    private void eliminarProgramacion(Long id) throws Exception {
        mvc.perform(delete("/api/reportes/programaciones/{id}", id).with(user(usuario).roles("superadmin")))
                .andExpect(status().isNoContent());
    }
}
