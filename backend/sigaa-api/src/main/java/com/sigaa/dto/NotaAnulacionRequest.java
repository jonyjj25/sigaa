package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NotaAnulacionRequest {
    @NotBlank(message = "Debes indicar por qué se anula la nota")
    @Size(min = 5, max = 1000, message = "El motivo debe tener entre 5 y 1000 caracteres")
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
