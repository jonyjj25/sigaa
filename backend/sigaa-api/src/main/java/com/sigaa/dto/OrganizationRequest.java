package com.sigaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizationRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 300)
    private String nombre;

    @Size(max = 30)
    private String siglas;

    @Size(max = 50)
    private String codigoInstitucional;

    @Size(max = 50)
    private String tipoOrganizacion;

    @Size(max = 30)
    private String ruc;

    @Size(max = 200)
    private String director;

    @Size(max = 200)
    private String directorCargo;

    @Size(max = 30)
    private String estado;

    private Boolean activo;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSiglas() { return siglas; }
    public void setSiglas(String siglas) { this.siglas = siglas; }
    public String getCodigoInstitucional() { return codigoInstitucional; }
    public void setCodigoInstitucional(String codigoInstitucional) { this.codigoInstitucional = codigoInstitucional; }
    public String getTipoOrganizacion() { return tipoOrganizacion; }
    public void setTipoOrganizacion(String tipoOrganizacion) { this.tipoOrganizacion = tipoOrganizacion; }
    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }
    public String getDirector() { return director; }
    public String getDirectorCargo() { return directorCargo; }
    public void setDirectorCargo(String value) { directorCargo = value; }
    public void setDirector(String director) { this.director = director; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
