package com.sigaa.dto;

import com.sigaa.model.Horario;
import java.time.LocalDate;

/** Contrato de Horarios: no conserva entidades ni relaciones JPA en la respuesta. */
public record HorarioResponse(
        Long id,
        UsuarioHorario usuario,
        int dia,
        String entrada,
        String salida,
        int tolerancia,
        String estado,
        LocalDate fechaInicio,
        LocalDate fechaFin) {

    public static HorarioResponse from(Horario horario) {
        var usuario = horario.getUsuario();
        var identidad = usuario == null ? null
                : new UsuarioHorario(usuario.getId(), usuario.getNombre(), usuario.getApellido());
        return new HorarioResponse(horario.getId(), identidad, horario.getDia(), horario.getEntrada(),
                horario.getSalida(), horario.getTolerancia(), horario.getEstado(),
                horario.getFechaInicio(), horario.getFechaFin());
    }

    public record UsuarioHorario(Long id, String nombre, String apellido) {}
}
