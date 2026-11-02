package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ReunionRequest {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 300, message = "El título no puede exceder 300 caracteres")
    private String titulo;

    @Size(max = 500)
    private String subtitulo;

    private String descripcion;

    private String objetivo;

    @NotBlank(message = "La fecha es obligatoria")
    private String fecha;

    private String fechaIso;

    @NotBlank(message = "La hora de inicio es obligatoria")
    private String horaInicio;

    @NotBlank(message = "La hora de fin es obligatoria")
    private String horaFin;

    @Size(max = 50)
    private String tipoReunion;

    @Size(max = 200)
    private String area;

    @Size(max = 200)
    private String organizador;

    private Long organizadorUsuarioId;

    @Size(max = 200)
    private String lugar;

    @Size(max = 50)
    private String modalidad;

    @Size(max = 500)
    private String enlaceVirtual;

    @Size(max = 200)
    private String sala;

    @Size(max = 200)
    private String unidadResponsable;

    @Size(max = 200)
    private String organizacion;

    private String estado;

    private List<String> participantesNombres;

    private List<Long> participanteUsuarioIds;

    private String observaciones;

    private List<Long> unidadIds;
    public List<Long> getUnidadIds() { return unidadIds; }
    public void setUnidadIds(List<Long> value) { unidadIds = value; }

    public ReunionRequest() {}

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getSubtitulo() { return subtitulo; }
    public void setSubtitulo(String subtitulo) { this.subtitulo = subtitulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getObjetivo() { return objetivo; }
    public void setObjetivo(String objetivo) { this.objetivo = objetivo; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getFechaIso() { return fechaIso; }
    public void setFechaIso(String fechaIso) { this.fechaIso = fechaIso; }
    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }
    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = horaFin; }
    public String getTipoReunion() { return tipoReunion; }
    public void setTipoReunion(String tipoReunion) { this.tipoReunion = tipoReunion; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getOrganizador() { return organizador; }
    public void setOrganizador(String organizador) { this.organizador = organizador; }
    public Long getOrganizadorUsuarioId() { return organizadorUsuarioId; }
    public void setOrganizadorUsuarioId(Long organizadorUsuarioId) { this.organizadorUsuarioId = organizadorUsuarioId; }
    public String getLugar() { return lugar; }
    public void setLugar(String lugar) { this.lugar = lugar; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public String getEnlaceVirtual() { return enlaceVirtual; }
    public void setEnlaceVirtual(String enlaceVirtual) { this.enlaceVirtual = enlaceVirtual; }
    public String getSala() { return sala; }
    public void setSala(String sala) { this.sala = sala; }
    public String getUnidadResponsable() { return unidadResponsable; }
    public void setUnidadResponsable(String unidadResponsable) { this.unidadResponsable = unidadResponsable; }
    public String getOrganizacion() { return organizacion; }
    public void setOrganizacion(String organizacion) { this.organizacion = organizacion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public List<String> getParticipantesNombres() { return participantesNombres; }
    public void setParticipantesNombres(List<String> participantesNombres) { this.participantesNombres = participantesNombres; }
    public List<Long> getParticipanteUsuarioIds() { return participanteUsuarioIds; }
    public void setParticipanteUsuarioIds(List<Long> participanteUsuarioIds) { this.participanteUsuarioIds = participanteUsuarioIds; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
