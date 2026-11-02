package com.sigaa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "backups")
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Column(length = 20)
    private String tamanio;

    @Column(length = 20)
    private String estado = "completado";

    @Column(length = 20)
    private String tipo = "automatico";

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Column(name = "copia_externa", nullable = false)
    private Boolean copiaExterna = false;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "verificacion_estado", nullable = false, length = 20)
    private String verificacionEstado = "pendiente";

    @Column(name = "verificado_at")
    private LocalDateTime verificadoAt;

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
    }

    public Backup() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTamanio() { return tamanio; }
    public void setTamanio(String tamanio) { this.tamanio = tamanio; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }
    public Boolean getCopiaExterna() { return copiaExterna; }
    public void setCopiaExterna(Boolean copiaExterna) { this.copiaExterna = copiaExterna; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public String getVerificacionEstado() { return verificacionEstado; }
    public void setVerificacionEstado(String verificacionEstado) { this.verificacionEstado = verificacionEstado; }
    public LocalDateTime getVerificadoAt() { return verificadoAt; }
    public void setVerificadoAt(LocalDateTime verificadoAt) { this.verificadoAt = verificadoAt; }
}
