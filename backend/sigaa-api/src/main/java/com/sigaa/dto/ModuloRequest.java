package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ModuloRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @NotBlank(message = "La ruta es obligatoria")
    @Size(max = 200)
    private String ruta;

    @Size(max = 100)
    private String icono;

    private Boolean activo;

    private Integer orden;

    @Size(max = 50)
    private String codigo;

    @Size(max = 500)
    private String descripcion;

    @Size(max = 50)
    private String categoria;

    @Size(max = 20)
    private String color;

    private Long padreId;

    @Size(max = 200)
    private String rutaApi;

    @Size(max = 30)
    private String tipoModulo;

    private Integer nivelJerarquico;

    @Size(max = 500)
    private String breadcrumb;

    @Size(max = 200)
    private String paginaInicio;

    @Size(max = 500)
    private String urlAyuda;

    @Size(max = 50)
    private String etiqueta;

    @Size(max = 200)
    private String componenteAngular;

    private Boolean visibleEnMenu;

    private Boolean visibleEnDashboard;

    private Boolean permiteAccesoDirecto;

    private Boolean requiereAutenticacion;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }
    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
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
    public String getRutaApi() { return rutaApi; }
    public void setRutaApi(String rutaApi) { this.rutaApi = rutaApi; }
    public String getTipoModulo() { return tipoModulo; }
    public void setTipoModulo(String tipoModulo) { this.tipoModulo = tipoModulo; }
    public Integer getNivelJerarquico() { return nivelJerarquico; }
    public void setNivelJerarquico(Integer nivelJerarquico) { this.nivelJerarquico = nivelJerarquico; }
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
}
