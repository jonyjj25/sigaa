package com.sigaa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventarioUpdateRequest extends InventarioActivoRequest {
    public InventarioUpdateRequest() {
        // Una edición no debe borrar una asignación por omitir este campo.
        setTipoAsignacion(null);
    }

    @NotNull(message = "Recarga el activo antes de editarlo")
    @PositiveOrZero(message = "La versión del activo no es válida")
    private Long lockVersion;
}
