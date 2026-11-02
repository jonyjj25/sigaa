package com.sigaa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public class NotaCreateRequest {
    @NotNull(message = "Debes seleccionar un número disponible")
    private Long numeroId;

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

    @Size(max = 15000)
    private String contenido;

    @NotNull(message = "Debes indicar quién dará seguimiento")
    private Long seguimientoUsuarioId;

    @Size(max = 5000)
    private String descripcion = "";

    private boolean sensitiva;

    @Valid
    @Size(max = 25, message = "Una nota puede enlazarse con hasta 25 notas al crearla")
    private List<NotaRelacionRequest> relaciones = new ArrayList<>();

    public Long getNumeroId() { return numeroId; }
    public void setNumeroId(Long numeroId) { this.numeroId = numeroId; }
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
    public void setDepartamentoId(Long departamentoId) { this.departamentoId = departamentoId; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public Long getSeguimientoUsuarioId() { return seguimientoUsuarioId; }
    public void setSeguimientoUsuarioId(Long seguimientoUsuarioId) { this.seguimientoUsuarioId = seguimientoUsuarioId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public boolean isSensitiva() { return sensitiva; }
    public void setSensitiva(boolean sensitiva) { this.sensitiva = sensitiva; }
    public List<NotaRelacionRequest> getRelaciones() { return relaciones; }
    public void setRelaciones(List<NotaRelacionRequest> relaciones) {
        this.relaciones = relaciones == null ? new ArrayList<>() : relaciones;
    }
}
