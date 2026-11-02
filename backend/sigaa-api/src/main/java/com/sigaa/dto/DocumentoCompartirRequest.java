package com.sigaa.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DocumentoCompartirRequest(
        @NotNull Long usuarioId,
        String permiso,
        LocalDate fechaExpiracion
) {}
