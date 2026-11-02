package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ParametroRequest {

    @Size(max = 100)
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 200)
    private String nombre;

    @NotBlank(message = "La clave es obligatoria")
    @Size(max = 100)
    private String clave;

    private String valor;

    private String valorDefecto;

    @Size(max = 50)
    private String tipo;

    @Size(max = 500)
    private String descripcion;

    @NotBlank(message = "El grupo es obligatorio")
    @Size(max = 100)
    private String grupo;

    @Size(max = 50)
    private String categoria;

    @Size(max = 30)
    private String estado;

    private Boolean activo;

    private Boolean visible;

    private Boolean editable;

    private Boolean requiereReinicio;

    private Long moduloId;

    private Integer orden;

    private String observaciones;

    private String fechaVigencia;

    private String fechaExpiracion;

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
    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getFechaVigencia() { return fechaVigencia; }
    public void setFechaVigencia(String fechaVigencia) { this.fechaVigencia = fechaVigencia; }
    public String getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(String fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
}
