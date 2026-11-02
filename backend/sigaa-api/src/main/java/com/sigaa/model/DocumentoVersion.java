package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documento_versiones")
public class DocumentoVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(length = 200)
    private String autor = "";

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(columnDefinition = "TEXT")
    private String cambios = "";

    private Integer numero;
    @Column(name = "archivo_ruta", length = 500) private String archivoRuta;
    @Column(name = "archivo_nombre", length = 255) private String archivoNombre;
    @Column(name = "contenido_hash", length = 64) private String contenidoHash;
    @Column(name = "tamano_bytes") private Long tamanoBytes;
    private Integer paginas;
    @Column(name = "autor_id") private Long autorId;
    @Column(name = "registrada_en") private LocalDateTime registradaEn;
    @Column(name = "linea_base", nullable = false) private boolean lineaBase;

    public DocumentoVersion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentoId() { return documentoId; }
    public void setDocumentoId(Long documentoId) { this.documentoId = documentoId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getCambios() { return cambios; }
    public void setCambios(String cambios) { this.cambios = cambios; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer value) { numero = value; }
    public String getArchivoRuta() { return archivoRuta; }
    public void setArchivoRuta(String value) { archivoRuta = value; }
    public String getArchivoNombre() { return archivoNombre; }
    public void setArchivoNombre(String value) { archivoNombre = value; }
    public String getContenidoHash() { return contenidoHash; }
    public void setContenidoHash(String value) { contenidoHash = value; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long value) { tamanoBytes = value; }
    public Integer getPaginas() { return paginas; }
    public void setPaginas(Integer value) { paginas = value; }
    public Long getAutorId() { return autorId; }
    public void setAutorId(Long value) { autorId = value; }
    public LocalDateTime getRegistradaEn() { return registradaEn; }
    public void setRegistradaEn(LocalDateTime value) { registradaEn = value; }
    public boolean isLineaBase() { return lineaBase; }
    public void setLineaBase(boolean value) { lineaBase = value; }
}
