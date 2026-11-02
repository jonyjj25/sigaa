package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos")
@org.hibernate.annotations.SQLDelete(sql = "UPDATE documentos SET eliminado = true, lock_version = lock_version + 1 WHERE id = ? AND lock_version = ?")
@org.hibernate.annotations.SQLRestriction("eliminado = false")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "creacion_id")
    private java.util.UUID creacionId;

    @Column(nullable = false, length = 300)
    private String nombre;

    @Column(length = 200)
    private String categoria = "";

    @Column(length = 200)
    private String area = "";

    @Column(length = 50)
    private String version = "1.0";

    @Column(name = "version_actual_id") private Long versionActualId;
    @Column(nullable = false) private boolean eliminado;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 200)
    private String propietario = "";

    @Column(name = "propietario_id")
    private Long propietarioUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentoClasificacion clasificacion = DocumentoClasificacion.CONFIDENCIAL;

    @Column(length = 50)
    private String estado = "borrador";

    @Column(columnDefinition = "TEXT")
    private String descripcion = "";

    @Column(nullable = false)
    private Integer paginas = 0;

    @Column(length = 50)
    private String tamano = "";

    @Column(nullable = false)
    private Boolean compartido = false;

    @Column(name = "archivo_ruta", length = 500)
    private String archivoRuta;

    @Column(length = 50)
    private String permiso = "solo_lectura";

    @Column(name = "carpeta_id")
    private Long carpetaId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "aprobador_id")
    private Long aprobadorId;

    @Column(name = "solicitado_por_id")
    private Long solicitadoPorId;

    public Documento() {}

    public Long getId() { return id; }
    public java.util.UUID getCreacionId() { return creacionId; }
    public void setCreacionId(java.util.UUID creacionId) { this.creacionId = creacionId; }
    public long getLockVersion() { return lockVersion; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Long getVersionActualId() { return versionActualId; }
    public void setVersionActualId(Long value) { versionActualId = value; }
    public boolean isEliminado() { return eliminado; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getPropietario() { return propietario; }
    public void setPropietario(String propietario) { this.propietario = propietario; }
    public Long getPropietarioUsuarioId() { return propietarioUsuarioId; }
    public void setPropietarioUsuarioId(Long propietarioUsuarioId) { this.propietarioUsuarioId = propietarioUsuarioId; }
    public DocumentoClasificacion getClasificacion() { return clasificacion; }
    public void setClasificacion(DocumentoClasificacion clasificacion) { this.clasificacion = clasificacion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getPaginas() { return paginas; }
    public void setPaginas(Integer paginas) { this.paginas = paginas; }
    public String getTamano() { return tamano; }
    public void setTamano(String tamano) { this.tamano = tamano; }
    public Boolean getCompartido() { return compartido; }
    public void setCompartido(Boolean compartido) { this.compartido = compartido; }
    public String getArchivoRuta() { return archivoRuta; }
    public void setArchivoRuta(String archivoRuta) { this.archivoRuta = archivoRuta; }
    public String getPermiso() { return permiso; }
    public void setPermiso(String permiso) { this.permiso = permiso; }
    public Long getCarpetaId() { return carpetaId; }
    public void setCarpetaId(Long carpetaId) { this.carpetaId = carpetaId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getAprobadorId() { return aprobadorId; }
    public void setAprobadorId(Long aprobadorId) { this.aprobadorId = aprobadorId; }
    public Long getSolicitadoPorId() { return solicitadoPorId; }
    public void setSolicitadoPorId(Long solicitadoPorId) { this.solicitadoPorId = solicitadoPorId; }
}
