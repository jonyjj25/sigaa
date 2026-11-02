package com.sigaa.dto;

public class PermissionResponse {

    private Long id;
    private String modulo;
    private String accion;
    private String recurso;
    private String descripcion;
    private Boolean activo;

    public PermissionResponse() {}

    public PermissionResponse(Long id, String modulo, String accion, String recurso, String descripcion, Boolean activo) {
        this.id = id;
        this.modulo = modulo;
        this.accion = accion;
        this.recurso = recurso;
        this.descripcion = descripcion;
        this.activo = activo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getRecurso() { return recurso; }
    public void setRecurso(String recurso) { this.recurso = recurso; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
