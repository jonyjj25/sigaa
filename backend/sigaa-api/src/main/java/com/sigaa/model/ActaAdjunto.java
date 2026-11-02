package com.sigaa.model;

import jakarta.persistence.*;

@Entity
@Table(name = "acta_adjuntos")
public class ActaAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "acta_id", nullable = false)
    private Long actaId;

    @Column(name = "nombre_original", nullable = false, length = 500)
    private String nombreOriginal;

    @Column(name = "nombre_archivo", nullable = false, length = 500)
    private String nombreArchivo;

    @Column(name = "tipo_mime", length = 100)
    private String tipoMime = "";

    @Column(nullable = false)
    private Long tamano = 0L;

    @Column(length = 1000)
    private String ruta = "";

    public ActaAdjunto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActaId() { return actaId; }
    public void setActaId(Long actaId) { this.actaId = actaId; }
    public String getNombreOriginal() { return nombreOriginal; }
    public void setNombreOriginal(String nombreOriginal) { this.nombreOriginal = nombreOriginal; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getTipoMime() { return tipoMime; }
    public void setTipoMime(String tipoMime) { this.tipoMime = tipoMime; }
    public Long getTamano() { return tamano; }
    public void setTamano(Long tamano) { this.tamano = tamano; }
    public String getRuta() { return ruta; }
    public void setRuta(String ruta) { this.ruta = ruta; }
}
