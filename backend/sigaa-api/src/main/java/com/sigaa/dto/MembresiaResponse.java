package com.sigaa.dto;

public class MembresiaResponse {

    private Long id;
    private Long unidadId;
    private String unidadNombre;
    private Long rolId;
    private String rolNombre;

    public MembresiaResponse() {}

    public MembresiaResponse(Long id, Long unidadId, String unidadNombre, Long rolId, String rolNombre) {
        this.id = id;
        this.unidadId = unidadId;
        this.unidadNombre = unidadNombre;
        this.rolId = rolId;
        this.rolNombre = rolNombre;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUnidadId() { return unidadId; }
    public void setUnidadId(Long unidadId) { this.unidadId = unidadId; }
    public String getUnidadNombre() { return unidadNombre; }
    public void setUnidadNombre(String unidadNombre) { this.unidadNombre = unidadNombre; }
    public Long getRolId() { return rolId; }
    public void setRolId(Long rolId) { this.rolId = rolId; }
    public String getRolNombre() { return rolNombre; }
    public void setRolNombre(String rolNombre) { this.rolNombre = rolNombre; }
}
