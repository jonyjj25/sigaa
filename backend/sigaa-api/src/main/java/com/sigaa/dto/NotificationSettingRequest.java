package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class NotificationSettingRequest {

    @NotNull(message = "El ID del evento es obligatorio")
    private Long eventoId;

    @NotBlank(message = "El canal es obligatorio")
    private String canal;

    private Boolean activo = true;
    private String prioridad = "normal";
    private String frecuencia = "inmediata";
    private String horarioInicio;
    private String horarioFin;
    private Boolean recordatorioActivo = false;
    private Integer recordatorioHoras = 0;
    private Integer vencimientoHoras = 0;
    private String mensajePersonalizado;
    private Long plantillaId;

    public NotificationSettingRequest() {}

    public Long getEventoId() { return eventoId; }
    public void setEventoId(Long eventoId) { this.eventoId = eventoId; }
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
}
