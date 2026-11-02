package com.sigaa.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Avances guardados como acciones del historial existente, sin reemplazar el estado documental. */
public enum NotaSeguimientoEstado {
    PENDIENTE_ENTREGA("Pendiente de entrega"),
    RECIBIDA("Recibida"),
    SIN_RESPUESTA("Sin respuesta"),
    RESPONDIDA("Respondida"),
    FIRMADA_RECEPTOR("Firmada por el receptor");

    private final String texto;

    NotaSeguimientoEstado(String texto) { this.texto = texto; }
    public String texto() { return texto; }
    public String autoridad() { return this == FIRMADA_RECEPTOR ? "Notas:Firmar" : "Notas:Cambiar seguimiento"; }

    public static List<String> acciones() { return Arrays.stream(values()).map(Enum::name).toList(); }
    public static Optional<NotaSeguimientoEstado> porAccion(String accion) {
        return Arrays.stream(values()).filter(estado -> estado.name().equals(accion)).findFirst();
    }
}
