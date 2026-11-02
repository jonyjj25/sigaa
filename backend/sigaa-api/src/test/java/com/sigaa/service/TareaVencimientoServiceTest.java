package com.sigaa.service;

import com.sigaa.model.Tarea;
import com.sigaa.repository.TareaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TareaVencimientoServiceTest {
    private TareaRepository tareas;
    private TareaVencimientoService service;

    @BeforeEach
    void setUp() {
        tareas = mock(TareaRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T05:30:00Z"), ZoneId.of("America/Panama"));
        service = new TareaVencimientoService(tareas, clock);
    }

    @Test
    void persistsOpenOverdueTasksUsingTheConfiguredLocalDate() {
        when(tareas.marcarAbiertasVencidas(any(), any())).thenReturn(2);

        assertEquals(2, service.actualizarVencidas());

        verify(tareas).marcarAbiertasVencidas(
            LocalDate.of(2026, 10, 27), LocalDateTime.of(2026, 10, 27, 0, 30));
    }

    @Test
    void immediatelyMarksOnlyOpenTasksWhoseFinishDateHasPassed() {
        Tarea abierta = tarea("en_progreso", LocalDate.of(2026, 10, 26));
        Tarea venceHoy = tarea("por_hacer", LocalDate.of(2026, 10, 27));
        Tarea completada = tarea("completada", LocalDate.of(2026, 10, 20));

        assertTrue(service.actualizarEstadoPorFecha(abierta));
        assertEquals("vencida", abierta.getEstado());
        assertFalse(service.actualizarEstadoPorFecha(venceHoy));
        assertEquals("por_hacer", venceHoy.getEstado());
        assertFalse(service.actualizarEstadoPorFecha(completada));
        assertEquals("completada", completada.getEstado());
    }

    @Test
    void reopensAnOverdueTaskWhenItsFinishDateIsMovedOrRemoved() {
        Tarea sinAvance = tarea("vencida", null);
        Tarea conAvance = tarea("vencida", LocalDate.of(2026, 10, 28));
        conAvance.setProgreso(40);
        Tarea completada = tarea("completada", LocalDate.of(2026, 10, 28));

        assertTrue(service.actualizarEstadoPorFecha(sinAvance));
        assertEquals("por_hacer", sinAvance.getEstado());
        assertTrue(service.actualizarEstadoPorFecha(conAvance));
        assertEquals("en_progreso", conAvance.getEstado());
        assertFalse(service.actualizarEstadoPorFecha(completada));
        assertEquals("completada", completada.getEstado());
    }

    private Tarea tarea(String estado, LocalDate fechaLimite) {
        Tarea tarea = new Tarea();
        tarea.setEstado(estado);
        tarea.setFechaLimite(fechaLimite);
        return tarea;
    }
}
