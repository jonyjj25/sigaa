package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private NotificationEvent evento;

    @Column(name = "canal", nullable = false, length = 30)
    private String canal;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(nullable = false, length = 20)
    private String prioridad = "normal";

    @Column(nullable = false, length = 30)
    private String frecuencia = "inmediata";

    @Column(name = "horario_inicio")
    private LocalTime horarioInicio;

    @Column(name = "horario_fin")
    private LocalTime horarioFin;

    @Column(name = "recordatorio_activo", nullable = false)
    private Boolean recordatorioActivo = false;

    @Column(name = "recordatorio_horas")
    private Integer recordatorioHoras = 0;

    @Column(name = "vencimiento_horas")
    private Integer vencimientoHoras = 0;

    @Column(name = "mensaje_personalizado", columnDefinition = "TEXT")
    private String mensajePersonalizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id")
    private NotificationTemplate plantilla;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "creado_por", length = 150)
    private String creadoPor;

    @Column(name = "modificado_por", length = 150)
    private String modificadoPor;

    @Column(name = "ip_creacion", length = 50)
    private String ipCreacion;

    @Column(name = "ip_modificacion", length = 50)
    private String ipModificacion;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public NotificationSetting() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public NotificationEvent getEvento() { return evento; }
    public void setEvento(NotificationEvent evento) { this.evento = evento; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public String getFrecuencia() { return frecuencia; }
    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }
    public LocalTime getHorarioInicio() { return horarioInicio; }
    public void setHorarioInicio(LocalTime horarioInicio) { this.horarioInicio = horarioInicio; }
    public LocalTime getHorarioFin() { return horarioFin; }
    public void setHorarioFin(LocalTime horarioFin) { this.horarioFin = horarioFin; }
    public Boolean getRecordatorioActivo() { return recordatorioActivo; }
    public void setRecordatorioActivo(Boolean recordatorioActivo) { this.recordatorioActivo = recordatorioActivo; }
    public Integer getRecordatorioHoras() { return recordatorioHoras; }
    public void setRecordatorioHoras(Integer recordatorioHoras) { this.recordatorioHoras = recordatorioHoras; }
    public Integer getVencimientoHoras() { return vencimientoHoras; }
    public void setVencimientoHoras(Integer vencimientoHoras) { this.vencimientoHoras = vencimientoHoras; }
    public String getMensajePersonalizado() { return mensajePersonalizado; }
    public void setMensajePersonalizado(String mensajePersonalizado) { this.mensajePersonalizado = mensajePersonalizado; }
    public NotificationTemplate getPlantilla() { return plantilla; }
    public void setPlantilla(NotificationTemplate plantilla) { this.plantilla = plantilla; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public String getModificadoPor() { return modificadoPor; }
    public void setModificadoPor(String modificadoPor) { this.modificadoPor = modificadoPor; }
    public String getIpCreacion() { return ipCreacion; }
    public void setIpCreacion(String ipCreacion) { this.ipCreacion = ipCreacion; }
    public String getIpModificacion() { return ipModificacion; }
    public void setIpModificacion(String ipModificacion) { this.ipModificacion = ipModificacion; }
}
