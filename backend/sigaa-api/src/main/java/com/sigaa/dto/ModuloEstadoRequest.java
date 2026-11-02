package com.sigaa.dto;

import jakarta.validation.constraints.NotNull;

public class ModuloEstadoRequest {

    @NotNull(message = "El estado es obligatorio")
    private Boolean activo;

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
