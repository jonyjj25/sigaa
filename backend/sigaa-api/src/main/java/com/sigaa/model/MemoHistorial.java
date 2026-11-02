package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "memo_historial")
public class MemoHistorial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "memo_id", nullable = false) private Memo memo;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @Column(nullable = false, length = 80) private String accion;
    @Column(name = "estado_anterior", length = 30) private String estadoAnterior;
    @Column(name = "estado_nuevo", length = 30) private String estadoNuevo;
    @Column(nullable = false, columnDefinition = "TEXT") private String detalle = "";
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Memo getMemo() { return memo; } public void setMemo(Memo value) { memo = value; }
    public Usuario getUsuario() { return usuario; } public void setUsuario(Usuario value) { usuario = value; }
    public String getAccion() { return accion; } public void setAccion(String value) { accion = value; }
    public String getEstadoAnterior() { return estadoAnterior; } public void setEstadoAnterior(String value) { estadoAnterior = value; }
    public String getEstadoNuevo() { return estadoNuevo; } public void setEstadoNuevo(String value) { estadoNuevo = value; }
    public String getDetalle() { return detalle; } public void setDetalle(String value) { detalle = value == null ? "" : value; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
