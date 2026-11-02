package com.sigaa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Versión inmutable de un PDF oficial asociado a una nota. */
@Entity
@Table(name = "nota_pdfs")
public class NotaPdf {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_id", nullable = false)
    private Nota nota;

    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion;

    @Column(name = "nombre_original", nullable = false, length = 300)
    private String nombreOriginal;

    @Column(name = "ruta_interna", nullable = false, length = 1000)
    private String rutaInterna;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime = "application/pdf";

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Boolean vigente = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eliminado_por_id")
    private Usuario eliminadoPor;

    @Column(name = "eliminado_at")
    private LocalDateTime eliminadoAt;

    @Column(name = "eliminacion_motivo", length = 1000)
    private String eliminacionMotivo;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Nota getNota() { return nota; }
    public void setNota(Nota nota) { this.nota = nota; }
    public Integer getNumeroVersion() { return numeroVersion; }
    public void setNumeroVersion(Integer numeroVersion) { this.numeroVersion = numeroVersion; }
    public String getNombreOriginal() { return nombreOriginal; }
    public void setNombreOriginal(String nombreOriginal) { this.nombreOriginal = nombreOriginal; }
    public String getRutaInterna() { return rutaInterna; }
    public void setRutaInterna(String rutaInterna) { this.rutaInterna = rutaInterna; }
    public String getTipoMime() { return tipoMime; }
    public void setTipoMime(String tipoMime) { this.tipoMime = tipoMime; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getVigente() { return vigente; }
    public void setVigente(Boolean vigente) { this.vigente = vigente; }
    public Usuario getEliminadoPor() { return eliminadoPor; }
    public void setEliminadoPor(Usuario eliminadoPor) { this.eliminadoPor = eliminadoPor; }
    public LocalDateTime getEliminadoAt() { return eliminadoAt; }
    public void setEliminadoAt(LocalDateTime eliminadoAt) { this.eliminadoAt = eliminadoAt; }
    public String getEliminacionMotivo() { return eliminacionMotivo; }
    public void setEliminacionMotivo(String eliminacionMotivo) { this.eliminacionMotivo = eliminacionMotivo; }
}
