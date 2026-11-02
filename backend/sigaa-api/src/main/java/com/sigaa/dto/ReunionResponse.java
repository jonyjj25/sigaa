package com.sigaa.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ReunionResponse {

    private Long id;
    private String codigo;
    private String fecha;
    private String fechaISO;
    private String horaInicio;
    private String horaFin;
    private String titulo;
    private String subtitulo;
    private String descripcion;
    private String objetivo;
    private String tipoReunion;
    private String area;
    private String organizador;
    private Long organizadorUsuarioId;
    private String lugar;
    private String modalidad;
    private String enlaceVirtual;
    private String sala;
    private String unidadResponsable;
    private String organizacion;
    private String estado;
    private Integer participantes;
    private List<String> participantesNombres;
    private List<Long> participanteUsuarioIds;
    private String observaciones;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean puedeIniciar;
    private boolean puedeFinalizar;
    private boolean puedeCancelar;
    private boolean puedeEditar;
    private boolean puedeEliminar;

    private List<java.util.Map<String, Object>> unidades;
    public List<java.util.Map<String, Object>> getUnidades() { return unidades; }
    public void setUnidades(List<java.util.Map<String, Object>> value) { unidades = value; }
    private List<Long> unidadIds;
    public List<Long> getUnidadIds() { return unidadIds; }
    public void setUnidadIds(List<Long> value) { unidadIds = value; }

    public ReunionResponse() {}

    public boolean isPuedeIniciar() { return puedeIniciar; }
    public void setPuedeIniciar(boolean value) { puedeIniciar = value; }
    public boolean isPuedeFinalizar() { return puedeFinalizar; }
    public void setPuedeFinalizar(boolean value) { puedeFinalizar = value; }
    public boolean isPuedeCancelar() { return puedeCancelar; }
    public void setPuedeCancelar(boolean value) { puedeCancelar = value; }
    public boolean isPuedeEditar() { return puedeEditar; }
    public void setPuedeEditar(boolean value) { puedeEditar = value; }
    public boolean isPuedeEliminar() { return puedeEliminar; }
    public void setPuedeEliminar(boolean value) { puedeEliminar = value; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getFechaISO() { return fechaISO; }
    public void setFechaISO(String fechaISO) { this.fechaISO = fechaISO; }
    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }
    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = horaFin; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getSubtitulo() { return subtitulo; }
    public void setSubtitulo(String subtitulo) { this.subtitulo = subtitulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getObjetivo() { return objetivo; }
    public void setObjetivo(String objetivo) { this.objetivo = objetivo; }
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
    public Integer getParticipantes() { return participantes; }
    public void setParticipantes(Integer participantes) { this.participantes = participantes; }
    public List<String> getParticipantesNombres() { return participantesNombres; }
    public void setParticipantesNombres(List<String> participantesNombres) { this.participantesNombres = participantesNombres; }
    public List<Long> getParticipanteUsuarioIds() { return participanteUsuarioIds; }
    public void setParticipanteUsuarioIds(List<Long> participanteUsuarioIds) { this.participanteUsuarioIds = participanteUsuarioIds; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
