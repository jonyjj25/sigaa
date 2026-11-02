package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documento_solicitudes_aprobacion")
public class DocumentoSolicitudAprobacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "documento_id", nullable = false) private Documento documento;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "solicitante_id", nullable = false) private Usuario solicitante;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "aprobador_id", nullable = false) private Usuario aprobador;
    @Column(nullable = false, length = 20) private String estado = "PENDIENTE";
    @Column(name = "fecha_solicitud", nullable = false) private LocalDateTime fechaSolicitud = LocalDateTime.now();
    @Column(name = "comentario_solicitud", columnDefinition = "TEXT") private String comentarioSolicitud = "";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "respondido_por_id") private Usuario respondidoPor;
    @Column(name = "fecha_respuesta") private LocalDateTime fechaRespuesta;
    @Column(name = "comentario_respuesta", columnDefinition = "TEXT") private String comentarioRespuesta = "";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "version_id") private DocumentoVersion version;
    public DocumentoVersion getVersion() { return version; }
    public void setVersion(DocumentoVersion value) { version = value; }
    public Long getId(){return id;} public Documento getDocumento(){return documento;} public void setDocumento(Documento v){documento=v;} public Usuario getSolicitante(){return solicitante;} public void setSolicitante(Usuario v){solicitante=v;} public Usuario getAprobador(){return aprobador;} public void setAprobador(Usuario v){aprobador=v;} public String getEstado(){return estado;} public void setEstado(String v){estado=v;} public LocalDateTime getFechaSolicitud(){return fechaSolicitud;} public void setFechaSolicitud(LocalDateTime v){fechaSolicitud=v;} public String getComentarioSolicitud(){return comentarioSolicitud;} public void setComentarioSolicitud(String v){comentarioSolicitud=v;} public Usuario getRespondidoPor(){return respondidoPor;} public void setRespondidoPor(Usuario v){respondidoPor=v;} public LocalDateTime getFechaRespuesta(){return fechaRespuesta;} public void setFechaRespuesta(LocalDateTime v){fechaRespuesta=v;} public String getComentarioRespuesta(){return comentarioRespuesta;} public void setComentarioRespuesta(String v){comentarioRespuesta=v;}
}
