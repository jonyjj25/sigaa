package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ReporteRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 300, message = "El nombre no puede exceder 300 caracteres")
    private String nombre;

    @NotBlank(message = "El módulo es obligatorio")
    @Size(max = 100)
    private String modulo;

    @NotBlank(message = "El tipo es obligatorio")
    @Pattern(regexp = "analitico|ejecutivo|detallado", message = "El tipo de reporte no es válido")
    private String tipo;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @Size(max = 200)
    private String generadoPor;

    @NotBlank(message = "El formato es obligatorio")
    @Pattern(regexp = "PDF|Excel|Word|CSV", message = "El formato del reporte no es válido")
    private String formato;

    @Size(max = 10000, message = "La descripción no puede exceder 10000 caracteres")
    private String descripcion;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getGeneradoPor() { return generadoPor; }
    public void setGeneradoPor(String generadoPor) { this.generadoPor = generadoPor; }
    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
