package com.sigaa.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record ReporteGeneracionRequest(
        @NotBlank @Pattern(regexp = "Reuniones|Actas|Asistencia|Tareas") String plantilla,
        @NotNull LocalDate fechaDesde,
        @NotNull LocalDate fechaHasta,
        @NotBlank @Pattern(regexp = "PDF|CSV") String formato) {

    @AssertTrue(message = "La fecha final debe ser igual o posterior a la fecha inicial")
    public boolean rangoValido() {
        return fechaDesde == null || fechaHasta == null || !fechaHasta.isBefore(fechaDesde);
    }
}
