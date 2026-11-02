package com.sigaa.dto;

import java.time.LocalDateTime;
import java.util.List;

public class NotificationEventResponse {

    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String categoria;
    private Boolean activo;
    private List<NotificationSettingResponse> settings;
    private List<NotificationRecipientResponse> recipients;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NotificationEventResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public List<NotificationSettingResponse> getSettings() { return settings; }
    public void setSettings(List<NotificationSettingResponse> settings) { this.settings = settings; }
    public List<NotificationRecipientResponse> getRecipients() { return recipients; }
    public void setRecipients(List<NotificationRecipientResponse> recipients) { this.recipients = recipients; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
