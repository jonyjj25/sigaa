package com.sigaa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/** Consecutivo institucional que puede ser reservado por una sola nota. */
@Entity
@Table(name = "nota_numeros")
public class NotaNumero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(nullable = false, length = 50)
    private String numero;

    @Column(nullable = false)
    private Integer anio = java.time.LocalDate.now(java.time.ZoneId.of("America/Panama")).getYear();
    private Integer consecutivo;

    @Column(nullable = false, length = 20)
    private String estado = "DISPONIBLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservado_por_id")
    private Usuario reservadoPor;

    @Column(name = "reservado_desde")
    private LocalDateTime reservadoDesde;

    @Column(name = "reservado_hasta")
    private LocalDateTime reservadoHasta;

    @OneToOne(mappedBy = "numero", fetch = FetchType.LAZY)
    private Nota nota;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getLockVersion() { return lockVersion; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public Integer getConsecutivo() { return consecutivo; }
    public void setConsecutivo(Integer consecutivo) { this.consecutivo = consecutivo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Usuario getReservadoPor() { return reservadoPor; }
    public void setReservadoPor(Usuario reservadoPor) { this.reservadoPor = reservadoPor; }
    public LocalDateTime getReservadoDesde() { return reservadoDesde; }
    public void setReservadoDesde(LocalDateTime reservadoDesde) { this.reservadoDesde = reservadoDesde; }
    public LocalDateTime getReservadoHasta() { return reservadoHasta; }
    public void setReservadoHasta(LocalDateTime reservadoHasta) { this.reservadoHasta = reservadoHasta; }
    public Nota getNota() { return nota; }
    public void setNota(Nota nota) { this.nota = nota; }
}
