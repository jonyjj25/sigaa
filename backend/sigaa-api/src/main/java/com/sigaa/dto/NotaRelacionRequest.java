package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class NotaRelacionRequest {
    @NotNull(message = "Debes seleccionar la nota relacionada")
    private Long notaRelacionadaId;

    @NotBlank(message = "Debes seleccionar el tipo de relación")
    @Pattern(regexp = "RELACIONADA_CON|RESPONDE_A|ANTECEDENTE_DE", message = "El tipo de relación no es válido")
    private String tipo;

    public Long getNotaRelacionadaId() { return notaRelacionadaId; }
    public void setNotaRelacionadaId(Long notaRelacionadaId) { this.notaRelacionadaId = notaRelacionadaId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}
