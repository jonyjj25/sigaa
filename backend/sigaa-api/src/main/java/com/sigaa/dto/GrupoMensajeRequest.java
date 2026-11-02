package com.sigaa.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import java.util.List;
public record GrupoMensajeRequest(@NotBlank @Size(max=2000) String contenido, List<Long> mencionadosIds) {}
