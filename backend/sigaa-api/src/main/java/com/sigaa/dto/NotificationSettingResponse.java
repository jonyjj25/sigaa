package com.sigaa.dto;

import java.time.LocalDateTime;

public class NotificationSettingResponse {

    private Long id;
    private Long eventoId;
    private String eventoNombre;
    private String eventoCodigo;
    private String canal;
    private Boolean activo;
    private String prioridad;
    private String frecuencia;
    private String horarioInicio;
    private String horarioFin;
    private Boolean recordatorioActivo;
    private Integer recordatorioHoras;
    private Integer vencimientoHoras;
    private String mensajePersonalizado;
    private Long plantillaId;
    private String plantillaNombre;
    private String creadoPor;
    private String modificadoPor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NotificationSettingResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventoId() { return eventoId; }
    public void setEventoId(Long eventoId) { this.eventoId = eventoId; }
    public String getEventoNombre() { return eventoNombre; }
    public void setEventoNombre(String eventoNombre) { this.eventoNombre = eventoNombre; }
    public String getEventoCodigo() { return eventoCodigo; }
    public void setEventoCodigo(String eventoCodigo) { this.eventoCodigo = eventoCodigo; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public String getFrecuencia() { return frecuencia; }
    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }
    public String getHorarioInicio() { return horarioInicio; }
    public void setHorarioInicio(String horarioInicio) { this.horarioInicio = horarioInicio; }
    public String getHorarioFin() { return horarioFin; }
    public void setHorarioFin(String horarioFin) { this.horarioFin = horarioFin; }
    public Boolean getRecordatorioActivo() { return recordatorioActivo; }
    public void setRecordatorioActivo(Boolean recordatorioActivo) { this.recordatorioActivo = recordatorioActivo; }
    public Integer getRecordatorioHoras() { return recordatorioHoras; }
    public void setRecordatorioHoras(Integer recordatorioHoras) { this.recordatorioHoras = recordatorioHoras; }
    public Integer getVencimientoHoras() { return vencimientoHoras; }
    public void setVencimientoHoras(Integer vencimientoHoras) { this.vencimientoHoras = vencimientoHoras; }
    public String getMensajePersonalizado() { return mensajePersonalizado; }
    public void setMensajePersonalizado(String mensajePersonalizado) { this.mensajePersonalizado = mensajePersonalizado; }
    public Long getPlantillaId() { return plantillaId; }
    public void setPlantillaId(Long plantillaId) { this.plantillaId = plantillaId; }
    public String getPlantillaNombre() { return plantillaNombre; }
    public void setPlantillaNombre(String plantillaNombre) { this.plantillaNombre = plantillaNombre; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public String getModificadoPor() { return modificadoPor; }
    public void setModificadoPor(String modificadoPor) { this.modificadoPor = modificadoPor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
