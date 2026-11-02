package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "acta_acuerdos")
public class ActaAcuerdo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "acta_id", nullable = false)
    private Long actaId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(length = 200)
    private String responsable = "";

    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;

    @Column(length = 50)
    private String estado = "Pendiente";

    public ActaAcuerdo() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActaId() { return actaId; }
    public void setActaId(Long actaId) { this.actaId = actaId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
