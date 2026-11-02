package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "memo_numeros")
public class MemoNumero {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;
    @Column(nullable = false, length = 50) private String numero;
    @Column(nullable = false, length = 20) private String estado = "DISPONIBLE";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reservado_por_id") private Usuario reservadoPor;
    @Column(name = "reservado_desde") private LocalDateTime reservadoDesde;
    @Column(name = "reservado_hasta") private LocalDateTime reservadoHasta;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @OneToOne(mappedBy = "numero", fetch = FetchType.LAZY) private Memo memo;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public long getLockVersion() { return lockVersion; }
    public String getNumero() { return numero; } public void setNumero(String numero) { this.numero = numero; }
    public String getEstado() { return estado; } public void setEstado(String estado) { this.estado = estado; }
    public Usuario getReservadoPor() { return reservadoPor; } public void setReservadoPor(Usuario value) { reservadoPor = value; }
    public LocalDateTime getReservadoDesde() { return reservadoDesde; } public void setReservadoDesde(LocalDateTime value) { reservadoDesde = value; }
    public LocalDateTime getReservadoHasta() { return reservadoHasta; } public void setReservadoHasta(LocalDateTime value) { reservadoHasta = value; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public Memo getMemo() { return memo; } public void setMemo(Memo value) { memo = value; }
}
