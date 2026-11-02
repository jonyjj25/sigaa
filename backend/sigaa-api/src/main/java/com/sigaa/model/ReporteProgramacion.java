package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "reporte_programaciones")
public class ReporteProgramacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String nombre;
    @Column(nullable = false, length = 100)
    private String modulo;
    @Column(nullable = false, length = 50)
    private String tipo = "ejecutivo";
    @Column(nullable = false, length = 50)
    private String formato = "PDF";
    @Column(nullable = false, length = 20)
    private String periodicidad;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;
    @Column(nullable = false)
    private LocalTime hora;
    @Column(nullable = false, length = 2000)
    private String destinatarios = "";
    @Column(name = "creado_por", nullable = false, length = 200)
    private String creadoPor = "sistema";
    @Column(name = "archivos_ruta", nullable = false, length = 2000)
    private String archivosRuta = "";
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "proxima_ejecucion", nullable = false)
    private LocalDateTime proximaEjecucion;
    @Column(name = "ultima_ejecucion")
    private LocalDateTime ultimaEjecucion;
    @Column(name = "ultimo_estado", nullable = false, length = 20)
    private String ultimoEstado = "PENDIENTE";
    @Column(name = "ultimo_error", nullable = false, length = 1000)
    private String ultimoError = "";
    @Version
    private long version;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }
    public String getPeriodicidad() { return periodicidad; }
    public void setPeriodicidad(String periodicidad) { this.periodicidad = periodicidad; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
    public String getDestinatarios() { return destinatarios; }
    public void setDestinatarios(String destinatarios) { this.destinatarios = destinatarios; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public String getArchivosRuta() { return archivosRuta; }
    public void setArchivosRuta(String archivosRuta) { this.archivosRuta = archivosRuta; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getProximaEjecucion() { return proximaEjecucion; }
    public void setProximaEjecucion(LocalDateTime proximaEjecucion) { this.proximaEjecucion = proximaEjecucion; }
    public LocalDateTime getUltimaEjecucion() { return ultimaEjecucion; }
    public void setUltimaEjecucion(LocalDateTime ultimaEjecucion) { this.ultimaEjecucion = ultimaEjecucion; }
    public String getUltimoEstado() { return ultimoEstado; }
    public void setUltimoEstado(String ultimoEstado) { this.ultimoEstado = ultimoEstado; }
    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
