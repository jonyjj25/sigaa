package com.sigaa.dto;
import jakarta.validation.constraints.*; import java.util.List;
public record GrupoRequest(@NotBlank(message="El nombre es obligatorio") @Size(max=150) String nombre, @Size(max=1000) String descripcion, @NotNull(message="La unidad es obligatoria") Long unidadId, @NotBlank(message="El tipo es obligatorio") @Size(max=80) String tipo, String icono, String color, Boolean activo, Long responsableId, List<Long> miembroIds, List<Long> permisoIds) {}
