package com.sigaa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ModuloConfiguracionRequest {

    @NotNull(message = "El identificador del módulo es obligatorio")
    private Long id;

    @NotNull(message = "El orden es obligatorio")
    @Positive(message = "El orden debe ser mayor que cero")
    private Integer orden;

    @NotNull(message = "El estado es obligatorio")
    private Boolean activo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
