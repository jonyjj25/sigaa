package com.sigaa.dto;

import jakarta.validation.constraints.*;

public class MemoCreateRequest {
    @NotNull(message = "Debes seleccionar un número disponible") private Long numeroId;
    @NotBlank(message = "El asunto es obligatorio") @Size(max = 200) private String asunto;
    @NotBlank(message = "El destinatario es obligatorio") @Size(max = 300) private String destino;
    @NotNull(message = "Debes indicar quién dará seguimiento") private Long seguimientoUsuarioId;
    @Size(max = 5000) private String descripcion = "";

    public Long getNumeroId() { return numeroId; } public void setNumeroId(Long value) { numeroId = value; }
    public String getAsunto() { return asunto; } public void setAsunto(String value) { asunto = value; }
    public String getDestino() { return destino; } public void setDestino(String value) { destino = value; }
    public Long getSeguimientoUsuarioId() { return seguimientoUsuarioId; } public void setSeguimientoUsuarioId(Long value) { seguimientoUsuarioId = value; }
    public String getDescripcion() { return descripcion; } public void setDescripcion(String value) { descripcion = value; }
}
