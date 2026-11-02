package com.sigaa.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TareaAsignacionRequest {
    private Long asignadoUsuarioId;
    private List<Long> participanteUsuarioIds;
    @Size(max = 200)
    private String asignado;
    @Pattern(regexp = "(?i)baja|media|alta|urgente", message = "La prioridad no es válida")
    private String prioridad;
    private LocalDate fechaLimite;
    private LocalTime horaLimite;

    public Long getAsignadoUsuarioId() { return asignadoUsuarioId; }
    public void setAsignadoUsuarioId(Long asignadoUsuarioId) { this.asignadoUsuarioId = asignadoUsuarioId; }
    public List<Long> getParticipanteUsuarioIds() { return participanteUsuarioIds; }
    public void setParticipanteUsuarioIds(List<Long> participanteUsuarioIds) { this.participanteUsuarioIds = participanteUsuarioIds; }
    public String getAsignado() { return asignado; }
    public void setAsignado(String asignado) { this.asignado = asignado; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
    public LocalTime getHoraLimite() { return horaLimite; }
    public void setHoraLimite(LocalTime horaLimite) { this.horaLimite = horaLimite; }
}
