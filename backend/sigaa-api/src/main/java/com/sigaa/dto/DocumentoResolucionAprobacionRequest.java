package com.sigaa.dto;
public record DocumentoResolucionAprobacionRequest(String decision, String comentario, Long solicitudId, Long versionId) {}
