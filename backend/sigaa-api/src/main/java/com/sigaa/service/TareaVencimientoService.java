package com.sigaa.service;

import com.sigaa.model.Tarea;
import com.sigaa.repository.TareaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Mantiene el estado vencido alineado con la fecha de finalización de la tarea. */
@Service
public class TareaVencimientoService {
    private static final Logger log = LoggerFactory.getLogger(TareaVencimientoService.class);

    private final TareaRepository tareas;
    private final Clock clock;

    @Autowired
    public TareaVencimientoService(TareaRepository tareas,
            @Value("${app.time-zone:America/Panama}") String timeZone) {
        this(tareas, Clock.system(ZoneId.of(timeZone)));
    }

    TareaVencimientoService(TareaRepository tareas, Clock clock) {
        this.tareas = tareas;
        this.clock = clock;
    }

    public LocalDate fechaActual() {
        return LocalDate.now(clock);
    }

    /**
     * Se ejecuta antes de listar para que una consulta iniciada tras medianoche no
     * dependa de haber esperado el siguiente ciclo programado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int actualizarVencidas() {
        return marcarVencidasActuales();
    }

    @Scheduled(cron = "${app.tareas.vencimiento-cron:0 5 0 * * *}",
               zone = "${app.time-zone:America/Panama}")
    @Transactional
    public void actualizarVencidasProgramadas() {
        int actualizadas = marcarVencidasActuales();
        if (actualizadas > 0) log.info("Se marcaron {} tareas vencidas", actualizadas);
    }

    /**
     * Reconcilia inmediatamente el estado temporal al crear o editar. Una
     * reprogramación también debe sacar la tarea de {@code vencida}; de otro
     * modo quedaría atrapada en ese estado aunque la nueva fecha aún no pase.
     */
    public boolean actualizarEstadoPorFecha(Tarea tarea) {
        if (tarea == null) return false;

        boolean fechaVencida = tarea.getFechaLimite() != null
            && tarea.getFechaLimite().isBefore(fechaActual());
        if (fechaVencida) {
            if (!"por_hacer".equals(tarea.getEstado()) && !"en_progreso".equals(tarea.getEstado())) {
                return false;
            }
            tarea.setEstado("vencida");
            return true;
        }

        if ("vencida".equals(tarea.getEstado())) {
            tarea.setEstado(tarea.getProgreso() != null && tarea.getProgreso() > 0
                ? "en_progreso" : "por_hacer");
            return true;
        }
        return false;
    }

    private int marcarVencidasActuales() {
        LocalDate fechaActual = fechaActual();
        return tareas.marcarAbiertasVencidas(fechaActual, LocalDateTime.now(clock));
    }
}
