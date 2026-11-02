package com.sigaa.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ModuloResponse {

    private Long id;
    private String nombre;
    private String ruta;
    private String icono;
    private Boolean activo;
    private int orden;
    private String codigo;
    private String descripcion;
    private String categoria;
    private String color;
    private Long padreId;
    private String padreNombre;
    private List<ModuloResponse> hijos;
    private String rutaApi;
    private String tipoModulo;
    private int nivelJerarquico;
    private String breadcrumb;
    private String paginaInicio;
    private String urlAyuda;
    private String etiqueta;
    private String componenteAngular;
    private Boolean visibleEnMenu;
    private Boolean visibleEnDashboard;
    private Boolean permiteAccesoDirecto;
    private Boolean requiereAutenticacion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ModuloResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }
    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Long getPadreId() { return padreId; }
    public void setPadreId(Long padreId) { this.padreId = padreId; }
    public String getPadreNombre() { return padreNombre; }
    public void setPadreNombre(String padreNombre) { this.padreNombre = padreNombre; }
    public List<ModuloResponse> getHijos() { return hijos; }
    public void setHijos(List<ModuloResponse> hijos) { this.hijos = hijos; }
    public String getRutaApi() { return rutaApi; }
    public void setRutaApi(String rutaApi) { this.rutaApi = rutaApi; }
    public String getTipoModulo() { return tipoModulo; }
    public void setTipoModulo(String tipoModulo) { this.tipoModulo = tipoModulo; }
    public int getNivelJerarquico() { return nivelJerarquico; }
    public void setNivelJerarquico(int nivelJerarquico) { this.nivelJerarquico = nivelJerarquico; }
    public String getBreadcrumb() { return breadcrumb; }
    public void setBreadcrumb(String breadcrumb) { this.breadcrumb = breadcrumb; }
    public String getPaginaInicio() { return paginaInicio; }
    public void setPaginaInicio(String paginaInicio) { this.paginaInicio = paginaInicio; }
    public String getUrlAyuda() { return urlAyuda; }
    public void setUrlAyuda(String urlAyuda) { this.urlAyuda = urlAyuda; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }
    public String getComponenteAngular() { return componenteAngular; }
    public void setComponenteAngular(String componenteAngular) { this.componenteAngular = componenteAngular; }
    public Boolean getVisibleEnMenu() { return visibleEnMenu; }
    public void setVisibleEnMenu(Boolean visibleEnMenu) { this.visibleEnMenu = visibleEnMenu; }
    public Boolean getVisibleEnDashboard() { return visibleEnDashboard; }
    public void setVisibleEnDashboard(Boolean visibleEnDashboard) { this.visibleEnDashboard = visibleEnDashboard; }
    public Boolean getPermiteAccesoDirecto() { return permiteAccesoDirecto; }
    public void setPermiteAccesoDirecto(Boolean permiteAccesoDirecto) { this.permiteAccesoDirecto = permiteAccesoDirecto; }
    public Boolean getRequiereAutenticacion() { return requiereAutenticacion; }
    public void setRequiereAutenticacion(Boolean requiereAutenticacion) { this.requiereAutenticacion = requiereAutenticacion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
