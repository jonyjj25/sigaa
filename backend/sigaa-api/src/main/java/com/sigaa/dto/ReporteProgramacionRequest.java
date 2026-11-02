package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReporteProgramacionRequest(
        @NotBlank @Size(max = 300) String nombre,
        @NotBlank @Pattern(regexp = "Reuniones|Actas|Asistencia|Actividades|Inventario|Tareas|Documentos|Usuarios") String modulo,
        @NotBlank @Pattern(regexp = "analitico|ejecutivo|detallado") String tipo,
        @NotBlank @Pattern(regexp = "PDF|Excel|Word|CSV") String formato,
        @NotBlank @Pattern(regexp = "diario|semanal|mensual") String periodicidad,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalTime hora,
        @Size(max = 2000) String destinatarios
) {}
