package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "carpeta_documentos")
public class CarpetaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "carpeta_id", nullable = false)
    private Long carpetaId;

    @Column(nullable = false, length = 300)
    private String nombre;

    @Column(length = 100)
    private String tipo = "";

    @Column(nullable = false)
    private LocalDate fecha;

    public CarpetaDocumento() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCarpetaId() { return carpetaId; }
    public void setCarpetaId(Long carpetaId) { this.carpetaId = carpetaId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}
