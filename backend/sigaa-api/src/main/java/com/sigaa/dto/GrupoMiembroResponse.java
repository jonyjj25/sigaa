package com.sigaa.dto;
import java.time.LocalDateTime;
public record GrupoMiembroResponse(Long id,Long usuarioId,String nombre,String correo,boolean responsable,boolean activo,LocalDateTime fechaAsignacion) {}
