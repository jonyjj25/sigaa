package com.sigaa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "notas")
public class Nota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_id", nullable = false, unique = true)
    private NotaNumero numero;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(nullable = false, length = 300)
    private String destino;

    @Column(name = "fecha_oficial", nullable = false)
    private LocalDate fechaOficial;

    @Column(name = "destinatario_nombre", nullable = false, length = 200)
    private String destinatarioNombre = "";

    @Column(name = "destinatario_cargo", nullable = false, length = 200)
    private String destinatarioCargo = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institucion_id")
    private Organization institucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departamento_id")
    private OrganizationDepartment departamento;

    // Nombre elegido al guardar: los cambios del catálogo no reescriben la nota.
    @Column(name = "departamento_nombre", length = 200)
    private String departamentoNombre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oficializada_por_id")
    private Usuario oficializadaPor;

    @Column(name = "oficializada_at")
    private LocalDateTime oficializadaAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firmante_id")
    private Usuario firmante;

    @Column(name = "firmante_nombre", length = 350)
    private String firmanteNombre;

    @Column(name = "firmante_cargo", length = 150)
    private String firmanteCargo;

    @Column(name = "firmante_institucion", length = 200)
    private String firmanteInstitucion;

    @Column(columnDefinition = "TEXT")
    private String descripcion = "";

    @Column(nullable = false)
    private boolean sensitiva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seguimiento_usuario_id", nullable = false)
    private Usuario seguimientoUsuario;

    @Column(nullable = false, length = 30)
    private String estado = "EN_SEGUIMIENTO";

    @Column(name = "pdf_nombre", length = 300)
    private String pdfNombre;

    @Column(name = "pdf_ruta", length = 1000)
    private String pdfRuta;

    @Column(name = "pdf_tamano_bytes")
    private Long pdfTamanoBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firmada_por_id")
    private Usuario firmadaPor;

    @Column(name = "firmada_at")
    private LocalDateTime firmadaAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrada_por_id")
    private Usuario cerradaPor;

    @Column(name = "cerrada_at")
    private LocalDateTime cerradaAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por_id")
    private Usuario anuladaPor;

    @Column(name = "anulada_at")
    private LocalDateTime anuladaAt;

    @Column(name = "anulacion_motivo", length = 1000)
    private String anulacionMotivo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getLockVersion() { return lockVersion; }
    public NotaNumero getNumero() { return numero; }
    public void setNumero(NotaNumero numero) { this.numero = numero; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public LocalDate getFechaOficial() { return fechaOficial; }
    public void setFechaOficial(LocalDate fechaOficial) { this.fechaOficial = fechaOficial; }
    public String getDestinatarioNombre() { return destinatarioNombre; }
    public void setDestinatarioNombre(String destinatarioNombre) { this.destinatarioNombre = destinatarioNombre == null ? "" : destinatarioNombre; }
    public String getDestinatarioCargo() { return destinatarioCargo; }
    public void setDestinatarioCargo(String destinatarioCargo) { this.destinatarioCargo = destinatarioCargo == null ? "" : destinatarioCargo; }
    public Organization getInstitucion() { return institucion; }
    public void setInstitucion(Organization institucion) { this.institucion = institucion; }
    public OrganizationDepartment getDepartamento() { return departamento; }
    public void setDepartamento(OrganizationDepartment departamento) { this.departamento = departamento; }
    public String getDepartamentoNombre() { return departamentoNombre; }
    public void setDepartamentoNombre(String departamentoNombre) { this.departamentoNombre = departamentoNombre; }
    public String getInstitucionNombreGuardado() {
        return destino != null && !destino.isBlank() ? destino : institucion == null ? "" : institucion.getNombre();
    }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido == null ? "" : contenido; }
    public Usuario getOficializadaPor() { return oficializadaPor; }
    public void setOficializadaPor(Usuario oficializadaPor) { this.oficializadaPor = oficializadaPor; }
    public LocalDateTime getOficializadaAt() { return oficializadaAt; }
    public void setOficializadaAt(LocalDateTime oficializadaAt) { this.oficializadaAt = oficializadaAt; }
    public Usuario getFirmante() { return firmante; }
    public void setFirmante(Usuario firmante) { this.firmante = firmante; }
    public String getFirmanteNombre() { return firmanteNombre; }
    public void setFirmanteNombre(String firmanteNombre) { this.firmanteNombre = firmanteNombre; }
    public String getFirmanteCargo() { return firmanteCargo; }
    public void setFirmanteCargo(String firmanteCargo) { this.firmanteCargo = firmanteCargo; }
    public String getFirmanteInstitucion() { return firmanteInstitucion; }
    public void setFirmanteInstitucion(String firmanteInstitucion) { this.firmanteInstitucion = firmanteInstitucion; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion == null ? "" : descripcion; }
    public boolean isSensitiva() { return sensitiva; }
    public void setSensitiva(boolean sensitiva) { this.sensitiva = sensitiva; }
    public Usuario getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Usuario creadoPor) { this.creadoPor = creadoPor; }
    public Usuario getSeguimientoUsuario() { return seguimientoUsuario; }
    public void setSeguimientoUsuario(Usuario seguimientoUsuario) { this.seguimientoUsuario = seguimientoUsuario; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getPdfNombre() { return pdfNombre; }
    public void setPdfNombre(String pdfNombre) { this.pdfNombre = pdfNombre; }
    public String getPdfRuta() { return pdfRuta; }
    public void setPdfRuta(String pdfRuta) { this.pdfRuta = pdfRuta; }
    public Long getPdfTamanoBytes() { return pdfTamanoBytes; }
    public void setPdfTamanoBytes(Long pdfTamanoBytes) { this.pdfTamanoBytes = pdfTamanoBytes; }
    public Usuario getFirmadaPor() { return firmadaPor; }
    public void setFirmadaPor(Usuario firmadaPor) { this.firmadaPor = firmadaPor; }
    public LocalDateTime getFirmadaAt() { return firmadaAt; }
    public void setFirmadaAt(LocalDateTime firmadaAt) { this.firmadaAt = firmadaAt; }
    public Usuario getCerradaPor() { return cerradaPor; }
    public void setCerradaPor(Usuario cerradaPor) { this.cerradaPor = cerradaPor; }
    public LocalDateTime getCerradaAt() { return cerradaAt; }
    public void setCerradaAt(LocalDateTime cerradaAt) { this.cerradaAt = cerradaAt; }
    public Usuario getAnuladaPor() { return anuladaPor; }
    public void setAnuladaPor(Usuario anuladaPor) { this.anuladaPor = anuladaPor; }
    public LocalDateTime getAnuladaAt() { return anuladaAt; }
    public void setAnuladaAt(LocalDateTime anuladaAt) { this.anuladaAt = anuladaAt; }
    public String getAnulacionMotivo() { return anulacionMotivo; }
    public void setAnulacionMotivo(String anulacionMotivo) { this.anulacionMotivo = anulacionMotivo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
