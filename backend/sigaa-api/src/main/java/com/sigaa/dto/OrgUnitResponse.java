package com.sigaa.dto;

import java.time.LocalDateTime;
import java.util.List;

public class OrgUnitResponse {

    private Long id;
    private String nombre;
    private String siglas;
    private String codigo;
    private String descripcion;
    private String tipo;
    private int nivel;
    private Long padreId;
    private String padreNombre;
    private String responsable;
    private String telefono;
    private String correo;
    private int orden;
    private Boolean activo;
    private List<OrgUnitResponse> hijos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSiglas() { return siglas; }
    public void setSiglas(String siglas) { this.siglas = siglas; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public int getNivel() { return nivel; }
    public void setNivel(int nivel) { this.nivel = nivel; }
    public Long getPadreId() { return padreId; }
    public void setPadreId(Long padreId) { this.padreId = padreId; }
    public String getPadreNombre() { return padreNombre; }
    public void setPadreNombre(String padreNombre) { this.padreNombre = padreNombre; }
    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public List<OrgUnitResponse> getHijos() { return hijos; }
    public void setHijos(List<OrgUnitResponse> hijos) { this.hijos = hijos; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
