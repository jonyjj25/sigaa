package com.sigaa.dto;

import java.util.List;
import java.util.Set;

public class UsuarioRequest {

    private String nombre;
    private String segundoNombre;
    private String apellido;
    private String correo;
    private String correoAlterno;
    private String password;
    private Boolean activo;
    private Boolean bloqueado;
    private String documentoIdentidad;
    private String tipoDocumento;
    private String telefono;
    private String celular;
    private String cargo;
    private String institucion;
    private String departamento;
    private String direccion;
    private String unidadAdministrativa;
    private String username;
    private Boolean cambioContrasenaRequerido;
    private String codigoInterno;
    private String observaciones;
    private Set<Long> rolIds;
    private List<MembresiaRequest> membresias;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSegundoNombre() { return segundoNombre; }
    public void setSegundoNombre(String segundoNombre) { this.segundoNombre = segundoNombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getCorreoAlterno() { return correoAlterno; }
    public void setCorreoAlterno(String correoAlterno) { this.correoAlterno = correoAlterno; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Boolean getBloqueado() { return bloqueado; }
    public void setBloqueado(Boolean bloqueado) { this.bloqueado = bloqueado; }
    public String getDocumentoIdentidad() { return documentoIdentidad; }
    public void setDocumentoIdentidad(String documentoIdentidad) { this.documentoIdentidad = documentoIdentidad; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
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
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Boolean getCambioContrasenaRequerido() { return cambioContrasenaRequerido; }
    public void setCambioContrasenaRequerido(Boolean cambioContrasenaRequerido) { this.cambioContrasenaRequerido = cambioContrasenaRequerido; }
    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public Set<Long> getRolIds() { return rolIds; }
    public void setRolIds(Set<Long> rolIds) { this.rolIds = rolIds; }
    public List<MembresiaRequest> getMembresias() { return membresias; }
    public void setMembresias(List<MembresiaRequest> membresias) { this.membresias = membresias; }
}
