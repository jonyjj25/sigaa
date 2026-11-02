package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reportes")
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String modulo;

    @Column(nullable = false, length = 50)
    private String tipo = "ejecutivo";

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "generado_por", length = 200)
    private String generadoPor = "";

    @Column(name = "creado_por_usuario_id", updatable = false)
    private Long creadoPorUsuarioId;

    @Column(nullable = false, length = 50)
    private String formato = "PDF";

    @Column(columnDefinition = "TEXT")
    private String descripcion = "";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "archivos_ruta", length = 2000)
    private String archivosRuta = "";

    @Column(columnDefinition = "TEXT")
    private String contenido = "";

    public Reporte() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getGeneradoPor() { return generadoPor; }
    public Long getCreadoPorUsuarioId() { return creadoPorUsuarioId; }
    public void setCreadoPorUsuarioId(Long id) { this.creadoPorUsuarioId = id; }
    public void setGeneradoPor(String generadoPor) { this.generadoPor = generadoPor; }
    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getArchivosRuta() { return archivosRuta; }
    public void setArchivosRuta(String archivosRuta) { this.archivosRuta = archivosRuta; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
}
