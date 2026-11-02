package com.sigaa.service;

import com.sigaa.util.LogDiagnostics;
import com.sigaa.model.Reporte;
import com.sigaa.model.ReporteProgramacion;
import com.sigaa.repository.ReporteProgramacionRepository;
import com.sigaa.repository.ReporteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class ReporteProgramacionService {
    private static final Logger log = LoggerFactory.getLogger(ReporteProgramacionService.class);
    private final ReporteProgramacionRepository programacionRepository;
    private final ReporteRepository reporteRepository;
    private final ReporteGenerationService generationService;
    private final ReporteDeliveryService deliveryService;
    private final ZoneId zoneId;
    private final TransactionTemplate executionTransaction;

    public ReporteProgramacionService(ReporteProgramacionRepository programacionRepository,
                                      ReporteRepository reporteRepository, ReporteGenerationService generationService,
                                      ReporteDeliveryService deliveryService,
                                      PlatformTransactionManager transactionManager,
                                      @Value("${app.time-zone:America/Panama}") String timeZone) {
        this.programacionRepository = programacionRepository;
        this.reporteRepository = reporteRepository;
        this.generationService = generationService;
        this.deliveryService = deliveryService;
        this.zoneId = ZoneId.of(timeZone);
        this.executionTransaction = new TransactionTemplate(transactionManager);
        this.executionTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${app.reports.scheduler-delay-ms:60000}")
    public void ejecutarPendientes() {
        LocalDateTime ahora = LocalDateTime.now(zoneId).truncatedTo(ChronoUnit.MICROS);
        for (Long id : programacionRepository.findPendientesIds(ahora, PageRequest.of(0, 50))) {
            ejecutar(id, ahora);
        }
    }

    void ejecutar(Long id, LocalDateTime ahora) {
        Ejecucion execution;
        try {
            execution = executionTransaction.execute(status -> generar(id, ahora));
        } catch (RuntimeException exception) {
            // La transacción de generación ya terminó (incluido un fallo al hacer commit).
            log.error("Falló la programación de reporte id={}: {}", id, LogDiagnostics.describe(exception));
            registrarFallo(id, ahora, exception);
            return;
        }
        if (execution == null) return;

        // execute solo retorna después de un commit exitoso. SMTP queda fuera de
        // la transacción y de los bloqueos; un rollback nunca llega a este punto.
        notificarConfirmado(execution, ahora);
    }

    private Ejecucion generar(Long id, LocalDateTime ahora) {
        ReporteProgramacion programacion = programacionRepository.findByIdForUpdate(id).orElse(null);
        // Otro planificador pudo ejecutar el mismo ID, o un usuario desactivarlo.
        if (!pendiente(programacion, ahora)) return null;
        Reporte saved;
        if (generationService.soporta(programacion.getModulo())) {
            LocalDateTime inicio = switch (programacion.getPeriodicidad()) {
                case "diario" -> ahora.minusDays(1);
                case "semanal" -> ahora.minusWeeks(1);
                case "mensual" -> ahora.minusMonths(1);
                default -> ahora.minusDays(30);
            };
            saved = generationService.generar(programacion.getNombre(), programacion.getModulo(),
                    programacion.getTipo(), programacion.getFormato(), inicio.toLocalDate(), ahora.toLocalDate(),
                    programacion.getCreadoPor());
            saved.setArchivosRuta(programacion.getArchivosRuta());
            saved = reporteRepository.save(saved);
        } else {
            Reporte reporte = new Reporte();
            reporte.setNombre(programacion.getNombre());
            reporte.setModulo(programacion.getModulo());
            reporte.setTipo(programacion.getTipo());
            reporte.setFormato(programacion.getFormato());
            reporte.setFecha(ahora.toLocalDate());
            reporte.setGeneradoPor(programacion.getCreadoPor());
            reporte.setDescripcion("Generado automáticamente por programación " + programacion.getPeriodicidad());
            reporte.setArchivosRuta(programacion.getArchivosRuta());
            saved = reporteRepository.save(reporte);
        }
        programacion.setUltimoEstado("SIN_ENVIO");
        programacion.setUltimoError("El reporte fue generado; la notificación está pendiente");
        avanzar(programacion, ahora);
        return new Ejecucion(programacion, saved);
    }

    private void registrarFallo(Long id, LocalDateTime ahora, RuntimeException exception) {
        try {
            executionTransaction.executeWithoutResult(status -> {
                ReporteProgramacion programacion = programacionRepository.findByIdForUpdate(id).orElse(null);
                if (!pendiente(programacion, ahora)) return;
                programacion.setUltimoEstado("FALLIDO");
                programacion.setUltimoError(limit(exception.getMessage(), 1000));
                avanzar(programacion, ahora);
            });
        } catch (RuntimeException persistenceException) {
            log.error("No se pudo registrar el fallo de la programación id={}: {}", id, LogDiagnostics.describe(persistenceException));
        }
    }

    private void notificarConfirmado(Ejecucion execution, LocalDateTime ahora) {
        boolean enviado = false;
        try {
            enviado = deliveryService.notificar(execution.programacion(), execution.reporte());
        } catch (RuntimeException exception) {
            log.error("Falló la notificación del reporte confirmado id={}: {}", execution.reporte().getId(), LogDiagnostics.describe(exception));
        }
        boolean notificado = enviado;
        try {
            executionTransaction.executeWithoutResult(status -> {
                ReporteProgramacion programacion = programacionRepository
                        .findByIdForUpdate(execution.programacion().getId()).orElse(null);
                // No sobreescribir el resultado de una ejecución más reciente.
                if (programacion == null || !ahora.equals(programacion.getUltimaEjecucion())) return;
                programacion.setUltimoEstado(notificado ? "EXITOSO" : "SIN_ENVIO");
                programacion.setUltimoError(notificado ? "" :
                        "El reporte fue generado, pero no se pudo enviar la notificación por correo");
                programacionRepository.save(programacion);
            });
        } catch (RuntimeException exception) {
            log.error("No se pudo registrar la notificación del reporte confirmado id={}: {}", execution.reporte().getId(), LogDiagnostics.describe(exception));
        }
    }

    private boolean pendiente(ReporteProgramacion programacion, LocalDateTime ahora) {
        return programacion != null && programacion.isActivo() && !programacion.getProximaEjecucion().isAfter(ahora);
    }

    private void avanzar(ReporteProgramacion programacion, LocalDateTime ahora) {
        programacion.setUltimaEjecucion(ahora);
        programacion.setProximaEjecucion(siguiente(programacion.getProximaEjecucion(), programacion.getPeriodicidad(), ahora));
        programacion.setUpdatedAt(ahora);
        programacionRepository.save(programacion);
    }

    private record Ejecucion(ReporteProgramacion programacion, Reporte reporte) { }

    public LocalDateTime siguiente(LocalDateTime base, String periodicidad, LocalDateTime ahora) {
        LocalDateTime siguiente = base;
        do {
            siguiente = switch (periodicidad) {
                case "diario" -> siguiente.plusDays(1);
                case "semanal" -> siguiente.plusWeeks(1);
                case "mensual" -> siguiente.plusMonths(1);
                default -> throw new IllegalArgumentException("Periodicidad no soportada");
            };
        } while (!siguiente.isAfter(ahora));
        return siguiente;
    }

    private String limit(String value, int max) {
        if (value == null) return "Error interno";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
