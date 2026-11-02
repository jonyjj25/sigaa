package com.sigaa.service;

import com.sigaa.model.Reporte;
import com.sigaa.model.ReporteProgramacion;
import com.sigaa.repository.ReporteProgramacionRepository;
import com.sigaa.repository.ReporteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ReporteProgramacionServiceTest {
    @Mock ReporteProgramacionRepository programacionRepository;
    @Mock ReporteRepository reporteRepository;
    @Mock ReporteGenerationService generationService;
    @Mock ReporteDeliveryService deliveryService;
    @Mock PlatformTransactionManager transactionManager;

    @Test
    void calculaLaSiguienteEjecucionSinDejarFechasVencidas() {
        ReporteProgramacionService service = new ReporteProgramacionService(programacionRepository, reporteRepository, generationService, deliveryService, transactionManager, "America/Panama");
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 8, 0);
        LocalDateTime ahora = LocalDateTime.of(2026, 8, 24, 9, 0);

        assertThat(service.siguiente(base, "diario", ahora)).isEqualTo(LocalDateTime.of(2026, 8, 25, 8, 0));
        assertThat(service.siguiente(base, "semanal", ahora)).isEqualTo(LocalDateTime.of(2026, 8, 29, 8, 0));
        assertThat(service.siguiente(base, "mensual", ahora)).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 0));
    }

    @Test
    void unaEjecucionCreaElReporteYActualizaLaProgramacion() {
        ReporteProgramacionService service = new ReporteProgramacionService(programacionRepository, reporteRepository, generationService, deliveryService, transactionManager, "America/Panama");
        ReporteProgramacion programacion = new ReporteProgramacion();
        org.springframework.test.util.ReflectionTestUtils.setField(programacion, "id", 1L);
        programacion.setNombre("Resumen semanal");
        programacion.setModulo("Tareas");
        programacion.setTipo("ejecutivo");
        programacion.setFormato("PDF");
        programacion.setPeriodicidad("semanal");
        programacion.setCreadoPor("admin@example.test");
        programacion.setArchivosRuta("");
        programacion.setProximaEjecucion(LocalDateTime.of(2026, 8, 24, 8, 0));
        LocalDateTime ahora = LocalDateTime.of(2026, 8, 24, 9, 0);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(programacionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(programacion));
        when(reporteRepository.save(any(Reporte.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryService.notificar(any(ReporteProgramacion.class), any(Reporte.class))).thenReturn(true);

        service.ejecutar(1L, ahora);

        ArgumentCaptor<Reporte> captor = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Resumen semanal");
        assertThat(programacion.getUltimoEstado()).isEqualTo("EXITOSO");
        assertThat(programacion.getUltimaEjecucion()).isEqualTo(ahora);
        assertThat(programacion.getProximaEjecucion()).isAfter(ahora);
        var order = inOrder(transactionManager, deliveryService);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
        order.verify(deliveryService).notificar(programacion, captor.getValue());
    }
}
