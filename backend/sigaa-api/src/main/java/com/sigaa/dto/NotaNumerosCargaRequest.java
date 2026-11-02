package com.sigaa.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class NotaNumerosCargaRequest {
    @NotEmpty(message = "Debes indicar al menos un número")
    @Size(max = 5000, message = "La carga admite hasta 5000 números por vez")
    private List<@Size(min = 1, max = 50) String> numeros;

    public List<String> getNumeros() { return numeros; }
    public void setNumeros(List<String> numeros) { this.numeros = numeros; }
}
