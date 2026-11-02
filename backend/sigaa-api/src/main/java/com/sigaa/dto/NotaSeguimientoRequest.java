package com.sigaa.dto;

import jakarta.validation.constraints.Size;

public record NotaSeguimientoRequest(
    @Size(max = 30) String estado,
    @Size(max = 5000, message = "El comentario admite hasta 5000 caracteres") String comentario
) {}
