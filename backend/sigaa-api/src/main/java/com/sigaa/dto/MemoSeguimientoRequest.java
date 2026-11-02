package com.sigaa.dto;

import jakarta.validation.constraints.*;

public class MemoSeguimientoRequest {
    @NotBlank(message = "Debes escribir la actualización del seguimiento")
    @Size(min = 3, max = 5000, message = "El seguimiento debe tener entre 3 y 5000 caracteres")
    private String descripcion;

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String value) { descripcion = value; }
}
