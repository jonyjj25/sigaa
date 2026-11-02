package com.sigaa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EstadoRequest {
    @NotBlank(message = "El estado es obligatorio")
    @Size(max = 50)
    private String estado;

    @Min(0)
    @Max(100)
    private Integer progreso;

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Integer getProgreso() { return progreso; }
    public void setProgreso(Integer progreso) { this.progreso = progreso; }
}
