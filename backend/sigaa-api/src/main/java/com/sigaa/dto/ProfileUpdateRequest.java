package com.sigaa.dto;

public class ProfileUpdateRequest {

    private String nombre;
    private String segundoNombre;
    private String apellido;
    private String telefono;
    private String celular;
    private String correoAlterno;
    private String documentoIdentidad;
    private String tipoDocumento;
    private String cargo;
    private String institucion;
    private String departamento;
    private String direccion;
    private String unidadAdministrativa;
    private String codigoInterno;
    private String observaciones;
    private String fotografiaRuta;

    public ProfileUpdateRequest() {}

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSegundoNombre() { return segundoNombre; }
    public void setSegundoNombre(String segundoNombre) { this.segundoNombre = segundoNombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public String getCorreoAlterno() { return correoAlterno; }
    public void setCorreoAlterno(String correoAlterno) { this.correoAlterno = correoAlterno; }
    public String getDocumentoIdentidad() { return documentoIdentidad; }
    public void setDocumentoIdentidad(String documentoIdentidad) { this.documentoIdentidad = documentoIdentidad; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getInstitucion() { return institucion; }
    public void setInstitucion(String institucion) { this.institucion = institucion; }
    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getUnidadAdministrativa() { return unidadAdministrativa; }
    public void setUnidadAdministrativa(String unidadAdministrativa) { this.unidadAdministrativa = unidadAdministrativa; }
    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getFotografiaRuta() { return fotografiaRuta; }
    public void setFotografiaRuta(String fotografiaRuta) { this.fotografiaRuta = fotografiaRuta; }
}
