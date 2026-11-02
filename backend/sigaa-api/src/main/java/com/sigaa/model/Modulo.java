package com.sigaa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modulos")
public class Modulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String ruta;

    @Column(length = 100)
    private String icono;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(nullable = false)
    private int orden;

    @Column(length = 50, unique = true)
    private String codigo;

    @Column(length = 500)
    private String descripcion;

    @Column(length = 50)
    private String categoria;

    @Column(length = 20)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id")
    private Modulo padre;

    @OneToMany(mappedBy = "padre", fetch = FetchType.LAZY)
    private List<Modulo> hijos = new ArrayList<>();

    @Column(length = 200)
    private String rutaApi;

    @Column(length = 30)
    private String tipoModulo;

    private int nivelJerarquico;

    @Column(length = 500)
    private String breadcrumb;

    @Column(length = 200)
    private String paginaInicio;

    @Column(length = 500)
    private String urlAyuda;

    @Column(length = 50)
    private String etiqueta;

    @Column(length = 200)
    private String componenteAngular;

    private Boolean visibleEnMenu = true;

    private Boolean visibleEnDashboard = true;

    private Boolean permiteAccesoDirecto = false;

    private Boolean requiereAutenticacion = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Modulo() {}

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
    public Modulo getPadre() { return padre; }
    public void setPadre(Modulo padre) { this.padre = padre; }
    public List<Modulo> getHijos() { return hijos; }
    public void setHijos(List<Modulo> hijos) { this.hijos = hijos; }
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
