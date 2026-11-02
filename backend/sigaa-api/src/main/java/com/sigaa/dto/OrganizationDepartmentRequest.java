package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationDepartmentRequest(
    @NotBlank(message = "El nombre del departamento es obligatorio") @Size(max = 200) String nombre,
    @Size(max = 200) String director,
    @Size(max = 200) String directorCargo,
    boolean activo
) {}
