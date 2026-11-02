package com.sigaa.dto;
import java.time.LocalDateTime; import java.util.List;
public record GrupoResponse(Long id,String nombre,String descripcion,Long unidadId,String unidadNombre,String tipo,String icono,String color,boolean activo,LocalDateTime creadoEn,String creadoPor,int miembros,Long responsableId,String responsableNombre,List<Long> permisoIds) {}
