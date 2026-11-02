package com.sigaa.dto;
import java.time.LocalDateTime;
public record GrupoMensajeResponse(Long id, Long autorId, String autorNombre, String contenido, LocalDateTime creadoEn) {}
