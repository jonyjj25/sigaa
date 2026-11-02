package com.sigaa.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Campos del inventario de oficina compartidos por creación y edición completa. */
@Getter
@Setter
public abstract class InventarioActivoRequest {
    @Size(max = 50)
    @Pattern(regexp = "INV-[0-9]{4}-[0-9]{3,}", message = "El código debe tener formato INV-AAAA-NNN")
    private String codigo;

    @NotBlank(message = "Indica el tipo de asignación")
    @Pattern(regexp = "SIN_ASIGNAR|PERSONA|UNIDAD", message = "Selecciona una asignación válida")
    private String tipoAsignacion = "SIN_ASIGNAR";
    @Positive(message = "Selecciona una persona válida")
    private Long asignadoUsuarioId;
    @Positive(message = "Selecciona una unidad válida")
    private Long asignadoUnidadId;

    @AssertTrue(message = "La asignación debe indicar una persona, una unidad o ningún destino, según el tipo elegido")
    public boolean isAsignacionValida() {
        return switch (tipoAsignacion == null ? "" : tipoAsignacion) {
            case "SIN_ASIGNAR" -> asignadoUsuarioId == null && asignadoUnidadId == null;
            case "PERSONA" -> asignadoUsuarioId != null && asignadoUnidadId == null;
            case "UNIDAD" -> asignadoUnidadId != null && asignadoUsuarioId == null;
            default -> false;
        };
    }
    @NotBlank(message = "El nombre del activo es obligatorio")
    @Size(max = 300, message = "El nombre admite hasta 300 caracteres")
    private String nombre;

    @NotBlank(message = "La categoría es obligatoria")
    @Size(max = 200, message = "La categoría admite hasta 200 caracteres")
    private String categoria;

    @NotBlank(message = "La ubicación física es obligatoria")
    @Size(max = 200, message = "La ubicación admite hasta 200 caracteres")
    private String ubicacion;

    @NotBlank(message = "El estado de funcionamiento es obligatorio")
    @Pattern(regexp = "en buen estado|mantenimiento|fuera de servicio|dañado",
            message = "Selecciona un estado de funcionamiento válido")
    private String estado;

    @Size(max = 200, message = "La unidad admite hasta 200 caracteres")
    private String unidad;
    @Size(max = 452, message = "El responsable admite hasta 452 caracteres")
    private String responsable;
    @Size(max = 200, message = "La marca / modelo admite hasta 200 caracteres")
    private String marcaModelo;
    @Size(max = 200, message = "La serie admite hasta 200 caracteres")
    @Pattern(regexp = "[^\\p{Cc}]*", message = "La serie no admite caracteres de control")
    private String serie;
    @Size(max = 200, message = "La serie institucional admite hasta 200 caracteres")
    @Pattern(regexp = "[^\\p{Cc}]*", message = "La serie institucional no admite caracteres de control")
    private String serieInstitucional;
    @PastOrPresent(message = "La fecha de adquisición no puede ser futura")
    private LocalDate fechaAdquisicion;
    @Size(max = 200, message = "El proveedor admite hasta 200 caracteres")
    private String proveedor;
    @Size(max = 5000, message = "La descripción admite hasta 5000 caracteres")
    private String descripcion;
}
