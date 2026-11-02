package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "acta_firmas")
public class ActaFirma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "acta_id", nullable = false)
    private Long actaId;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 200)
    private String rol = "";

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public ActaFirma() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActaId() { return actaId; }
    public void setActaId(Long actaId) { this.actaId = actaId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
