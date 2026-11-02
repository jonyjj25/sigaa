package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "memos")
public class Memo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "numero_id", nullable = false, unique = true) private MemoNumero numero;
    @Column(nullable = false, length = 200) private String asunto;
    @Column(nullable = false, length = 300) private String destino;
    @Column(nullable = false, columnDefinition = "TEXT") private String descripcion = "";
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creado_por_id", nullable = false) private Usuario creadoPor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "seguimiento_usuario_id", nullable = false) private Usuario seguimientoUsuario;
    @Column(nullable = false, length = 30) private String estado = "EN_SEGUIMIENTO";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "completado_por_id") private Usuario completadoPor;
    @Column(name = "completado_at") private LocalDateTime completadoAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "anulado_por_id") private Usuario anuladoPor;
    @Column(name = "anulado_at") private LocalDateTime anuladoAt;
    @Column(name = "anulacion_motivo", length = 1000) private String anulacionMotivo;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); if (createdAt == null) createdAt = now; if (updatedAt == null) updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public MemoNumero getNumero() { return numero; } public void setNumero(MemoNumero value) { numero = value; }
    public String getAsunto() { return asunto; } public void setAsunto(String value) { asunto = value; }
    public String getDestino() { return destino; } public void setDestino(String value) { destino = value; }
    public String getDescripcion() { return descripcion; } public void setDescripcion(String value) { descripcion = value == null ? "" : value; }
    public Usuario getCreadoPor() { return creadoPor; } public void setCreadoPor(Usuario value) { creadoPor = value; }
    public Usuario getSeguimientoUsuario() { return seguimientoUsuario; } public void setSeguimientoUsuario(Usuario value) { seguimientoUsuario = value; }
    public String getEstado() { return estado; } public void setEstado(String value) { estado = value; }
    public Usuario getCompletadoPor() { return completadoPor; } public void setCompletadoPor(Usuario value) { completadoPor = value; }
    public LocalDateTime getCompletadoAt() { return completadoAt; } public void setCompletadoAt(LocalDateTime value) { completadoAt = value; }
    public Usuario getAnuladoPor() { return anuladoPor; } public void setAnuladoPor(Usuario value) { anuladoPor = value; }
    public LocalDateTime getAnuladoAt() { return anuladoAt; } public void setAnuladoAt(LocalDateTime value) { anuladoAt = value; }
    public String getAnulacionMotivo() { return anulacionMotivo; } public void setAnulacionMotivo(String value) { anulacionMotivo = value; }
    public LocalDateTime getCreatedAt() { return createdAt; } public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
