package com.sigaa.service;

import com.sigaa.model.Reporte;
import com.sigaa.model.ReporteProgramacion;
import com.sigaa.repository.ReporteProgramacionRepository;
import com.sigaa.repository.ReporteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

@SpringBootTest(properties = "app.reports.scheduler-delay-ms=3600000")
@ActiveProfiles("test")
class ReporteProgramacionTransactionIntegrationTest {
    @Autowired ReporteProgramacionRepository programaciones;
    @Autowired ReporteRepository reportes;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    private ReporteGenerationService generation;
    private ReporteDeliveryService delivery;
    private ReporteProgramacionRepository selected;
    private ReporteProgramacionService service;
    private final List<Long> ids = new ArrayList<>();
    private final String creator = "schedule-tx-" + UUID.randomUUID();
    private final LocalDateTime ahora = LocalDateTime.now().withNano(0);

    @BeforeEach void preparar() {
        generation = mock(ReporteGenerationService.class);
        delivery = mock(ReporteDeliveryService.class);
        selected = mock(ReporteProgramacionRepository.class, delegatesTo(programaciones));
        service = new ReporteProgramacionService(selected, reportes, generation, delivery, transactionManager, "America/Panama");
    }

    @AfterEach void limpiar() {
        jdbc.update("DELETE FROM reportes WHERE generado_por = ?", creator);
        ids.forEach(id -> jdbc.update("DELETE FROM reporte_programaciones WHERE id = ?", id));
    }

    @Test void rollbackDeUnaEjecucionNoRevierteLasCorrectasDelLote() {
        var primera = crear("Primera");
        var fallida = crear("Fallida");
        var ultima = crear("Última");
        doReturn(List.of(primera.getId(), fallida.getId(), ultima.getId()))
                .when(selected).findPendientesIds(any(), any());
        when(generation.soporta(anyString())).thenReturn(true);
        when(generation.generar(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String nombre = invocation.getArgument(0);
                    Reporte reporte = insertar(nombre);
                    if (nombre.equals("Fallida")) {
                        // Simula un servicio REQUIRED que marca la transacción compartida
                        // para rollback, aunque su excepción sea capturada por el llamador.
                        try {
                            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                                throw new IllegalStateException("Fallo interno del generador");
                            });
                        } catch (IllegalStateException expected) { /* La transacción sigue rollback-only. */ }
                    }
                    return reporte;
                });
        when(delivery.notificar(any(), any())).thenReturn(true);

        service.ejecutarPendientes();

        assertThat(nombresReportes()).containsExactlyInAnyOrder("Primera", "Última");
        comprobarEstado(primera, "EXITOSO");
        comprobarEstado(fallida, "FALLIDO");
        comprobarEstado(ultima, "EXITOSO");
        verify(delivery, times(2)).notificar(any(), any());
        verify(delivery, never()).notificar(argThat(p -> p.getId().equals(fallida.getId())), any());
    }

    @Test void correoSoloSeEnviaCuandoLosDatosYaSonVisiblesTrasCommit() {
        var programacion = crear("Confirmado");
        when(delivery.notificar(any(), any())).thenAnswer(invocation -> {
            Reporte reporte = invocation.getArgument(1);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(reportes.findById(reporte.getId())).isPresent();
            assertThat(programaciones.findById(programacion.getId()).orElseThrow().getProximaEjecucion()).isAfter(ahora);
            return true;
        });

        service.ejecutar(programacion.getId(), ahora);

        comprobarEstado(programacion, "EXITOSO");
        verify(delivery).notificar(any(), any());
    }

    @Test void falloAlConfirmarRevierteReporteYNoEnviaCorreo() {
        var programacion = crear("Commit fallido");
        when(generation.soporta(anyString())).thenReturn(true);
        when(generation.generar(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    Reporte reporte = insertar("Commit fallido");
                    // INSERT válido; el UPDATE inválido se difiere hasta flush/commit.
                    reporte.setNombre("x".repeat(301));
                    return reporte;
                });

        service.ejecutar(programacion.getId(), ahora);

        assertThat(nombresReportes()).isEmpty();
        comprobarEstado(programacion, "FALLIDO");
        verifyNoInteractions(delivery);
    }

    @Test void excepcionDuranteGeneracionRevierteLosCambiosParciales() {
        var programacion = crear("Generación fallida");
        when(generation.soporta(anyString())).thenReturn(true);
        when(generation.generar(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    insertar("Parcial");
                    throw new IllegalStateException("Fallo tras insertar");
                });

        service.ejecutar(programacion.getId(), ahora);

        assertThat(nombresReportes()).isEmpty();
        comprobarEstado(programacion, "FALLIDO");
        verifyNoInteractions(delivery);
    }

    @Test void falloDeCorreoConservaElReporteConfirmado() {
        var programacion = crear("Sin correo");
        when(delivery.notificar(any(), any())).thenReturn(false);

        service.ejecutar(programacion.getId(), ahora);

        assertThat(nombresReportes()).containsExactly("Sin correo");
        comprobarEstado(programacion, "SIN_ENVIO");
    }

    @Test void idYaProcesadoNoGeneraNiNotificaDeNuevo() {
        var programacion = crear("Único");
        when(delivery.notificar(any(), any())).thenReturn(true);

        service.ejecutar(programacion.getId(), ahora);
        service.ejecutar(programacion.getId(), ahora);

        assertThat(nombresReportes()).containsExactly("Único");
        verify(delivery, times(1)).notificar(any(), any());
    }

    private ReporteProgramacion crear(String nombre) {
        var p = new ReporteProgramacion();
        p.setNombre(nombre);
        p.setModulo("Tareas");
        p.setPeriodicidad("diario");
        p.setFechaInicio(ahora.toLocalDate());
        p.setHora(LocalTime.of(8, 0));
        p.setProximaEjecucion(ahora.minusMinutes(1));
        p.setCreadoPor(creator);
        p.setDestinatarios("synthetic@example.test");
        p = programaciones.saveAndFlush(p);
        ids.add(p.getId());
        return p;
    }

    private Reporte insertar(String nombre) {
        var r = new Reporte();
        r.setNombre(nombre);
        r.setModulo("Tareas");
        r.setFecha(LocalDate.now());
        r.setGeneradoPor(creator);
        return reportes.saveAndFlush(r);
    }

    private List<String> nombresReportes() {
        return jdbc.queryForList("SELECT nombre FROM reportes WHERE generado_por = ?", String.class, creator);
    }

    private void comprobarEstado(ReporteProgramacion p, String estado) {
        var actual = programaciones.findById(p.getId()).orElseThrow();
        assertThat(actual.getUltimoEstado()).isEqualTo(estado);
        assertThat(actual.getUltimaEjecucion()).isNotNull();
        assertThat(actual.getProximaEjecucion()).isAfter(ahora);
    }
}
