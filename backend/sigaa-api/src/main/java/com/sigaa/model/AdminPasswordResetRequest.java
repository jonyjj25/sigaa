package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_password_reset_requests")
public class AdminPasswordResetRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private Usuario usuario;
    @Column(nullable = false, length = 20) private String status = "PENDIENTE";
    @Column(name = "request_ip", length = 45) private String requestIp;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_by") private Usuario resolvedBy;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;
    @Column(name = "temporary_password_expires_at") private LocalDateTime temporaryPasswordExpiresAt;

    @PrePersist void prePersist() { if (requestedAt == null) requestedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestIp() { return requestIp; }
    public void setRequestIp(String requestIp) { this.requestIp = requestIp; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public Usuario getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Usuario resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }
    public void setTemporaryPasswordExpiresAt(LocalDateTime value) { this.temporaryPasswordExpiresAt = value; }
}
