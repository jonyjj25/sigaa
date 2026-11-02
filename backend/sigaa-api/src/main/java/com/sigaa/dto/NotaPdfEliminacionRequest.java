package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NotaPdfEliminacionRequest {
    @NotBlank(message = "Debes indicar por qué se elimina el archivo")
    @Size(min = 5, max = 1000, message = "El motivo debe tener entre 5 y 1000 caracteres")
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
