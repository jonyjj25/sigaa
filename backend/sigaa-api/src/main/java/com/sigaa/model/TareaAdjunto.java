package com.sigaa.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tarea_adjuntos")
public class TareaAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tarea_id", nullable = false)
    private Long tareaId;

    @Column(nullable = false, length = 300)
    private String nombre;

    @Column(length = 50)
    private String tamano = "";

    @Column(name = "ruta_archivo", length = 1000)
    private String rutaArchivo;

    @Column(name = "tipo_contenido", length = 100)
    private String tipoContenido;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String value) { rutaArchivo = value; }
    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String value) { tipoContenido = value; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long value) { tamanoBytes = value; }

    public TareaAdjunto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTareaId() { return tareaId; }
    public void setTareaId(Long tareaId) { this.tareaId = tareaId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTamano() { return tamano; }
    public void setTamano(String tamano) { this.tamano = tamano; }
}
