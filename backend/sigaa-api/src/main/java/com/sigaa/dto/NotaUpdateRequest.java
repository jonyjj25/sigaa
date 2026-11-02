package com.sigaa.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class NotaUpdateRequest {
    private LocalDate fechaOficial;

    public LocalDate getFechaOficial() { return fechaOficial; }
    public void setFechaOficial(LocalDate fechaOficial) { this.fechaOficial = fechaOficial; }

    @Size(max = 200)
    private String asunto;

    @Size(max = 300)
    private String destino;

    @Size(max = 200)
    private String destinatarioNombre;

    @Size(max = 200)
    private String destinatarioCargo;

    private Long institucionId;

    private Long departamentoId;
    private boolean departamentoDefinido;

    @Size(max = 15000)
    private String contenido;

    @NotNull(message = "Debes indicar quién dará seguimiento")
    private Long seguimientoUsuarioId;

    @Size(max = 5000)
    private String descripcion = "";

    private boolean sensitiva;

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public String getDestinatarioNombre() { return destinatarioNombre; }
    public void setDestinatarioNombre(String destinatarioNombre) { this.destinatarioNombre = destinatarioNombre; }
    public String getDestinatarioCargo() { return destinatarioCargo; }
    public void setDestinatarioCargo(String destinatarioCargo) { this.destinatarioCargo = destinatarioCargo; }
    public Long getInstitucionId() { return institucionId; }
    public void setInstitucionId(Long institucionId) { this.institucionId = institucionId; }
    public Long getDepartamentoId() { return departamentoId; }
    public void setDepartamentoId(Long departamentoId) {
        this.departamentoId = departamentoId;
        this.departamentoDefinido = true;
    }
    /** Omitir el campo conserva el vínculo de clientes anteriores; null explícito lo limpia. */
    @JsonIgnore
    public boolean isDepartamentoDefinido() { return departamentoDefinido; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public Long getSeguimientoUsuarioId() { return seguimientoUsuarioId; }
    public void setSeguimientoUsuarioId(Long seguimientoUsuarioId) { this.seguimientoUsuarioId = seguimientoUsuarioId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public boolean isSensitiva() { return sensitiva; }
    public void setSensitiva(boolean sensitiva) { this.sensitiva = sensitiva; }
}
