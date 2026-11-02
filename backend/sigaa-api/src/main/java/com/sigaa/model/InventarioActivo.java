package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventario_activos")
public class InventarioActivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 300)
    private String nombre;

    @Column(length = 200)
    private String categoria = "";

    @Column(length = 200)
    private String ubicacion = "";

    @Column(length = 200, nullable = false)
    private String unidad = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_usuario_id")
    private Usuario asignadoUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_unidad_id")
    private OrgUnit asignadoUnidad;

    @Column(length = 50)
    private String estado = "en buen estado";

    @Column(name = "fecha_adquisicion")
    private LocalDate fechaAdquisicion;

    @Column(name = "marca_modelo", length = 200)
    private String marcaModelo = "";

    @Column(length = 200)
    private String serie = "";

    @Column(name = "serie_institucional", length = 200)
    private String serieInstitucional = "";

    @Column(length = 452)
    private String responsable = "";

    @Column(length = 200)
    private String proveedor = "";

    @Column(columnDefinition = "TEXT")
    private String descripcion = "";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public InventarioActivo() {}

    public Long getId() { return id; }
    public long getLockVersion() { return lockVersion; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public Usuario getAsignadoUsuario() { return asignadoUsuario; }
    public void setAsignadoUsuario(Usuario usuario) { this.asignadoUsuario = usuario; }
    public OrgUnit getAsignadoUnidad() { return asignadoUnidad; }
    public void setAsignadoUnidad(OrgUnit unidad) { this.asignadoUnidad = unidad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDate getFechaAdquisicion() { return fechaAdquisicion; }
    public void setFechaAdquisicion(LocalDate fechaAdquisicion) { this.fechaAdquisicion = fechaAdquisicion; }
    public String getMarcaModelo() { return marcaModelo; }
    public void setMarcaModelo(String marcaModelo) { this.marcaModelo = marcaModelo; }
    public String getSerie() { return serie; }
    public void setSerie(String serie) { this.serie = serie; }
    public String getSerieInstitucional() { return serieInstitucional; }
    public void setSerieInstitucional(String serieInstitucional) { this.serieInstitucional = serieInstitucional; }
    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }
    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
