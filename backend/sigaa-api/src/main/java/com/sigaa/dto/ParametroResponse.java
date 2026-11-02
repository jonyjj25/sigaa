package com.sigaa.dto;

import java.time.LocalDateTime;

public class ParametroResponse {

    private Long id;
    private String codigo;
    private String nombre;
    private String clave;
    private String valor;
    private String valorDefecto;
    private String tipo;
    private String descripcion;
    private String grupo;
    private String categoria;
    private String estado;
    private Boolean activo;
    private Boolean visible;
    private Boolean editable;
    private Boolean requiereReinicio;
    private Long moduloId;
    private String moduloNombre;
    private int orden;
    private String observaciones;
    private LocalDateTime fechaVigencia;
    private LocalDateTime fechaExpiracion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ParametroResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
    public String getValorDefecto() { return valorDefecto; }
    public void setValorDefecto(String valorDefecto) { this.valorDefecto = valorDefecto; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public Boolean getEditable() { return editable; }
    public void setEditable(Boolean editable) { this.editable = editable; }
    public Boolean getRequiereReinicio() { return requiereReinicio; }
    public void setRequiereReinicio(Boolean requiereReinicio) { this.requiereReinicio = requiereReinicio; }
    public Long getModuloId() { return moduloId; }
    public void setModuloId(Long moduloId) { this.moduloId = moduloId; }
    public String getModuloNombre() { return moduloNombre; }
    public void setModuloNombre(String moduloNombre) { this.moduloNombre = moduloNombre; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public LocalDateTime getFechaVigencia() { return fechaVigencia; }
    public void setFechaVigencia(LocalDateTime fechaVigencia) { this.fechaVigencia = fechaVigencia; }
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
