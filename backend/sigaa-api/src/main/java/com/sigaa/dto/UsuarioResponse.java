package com.sigaa.dto;

import java.time.LocalDateTime;
import java.util.List;

public class UsuarioResponse {

    private Long id;
    private String nombre;
    private String segundoNombre;
    private String apellido;
    private String correo;
    private String correoAlterno;
    private Boolean activo;
    private Boolean bloqueado;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private LocalDateTime ultimoAcceso;
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
    private LocalDateTime fechaExpiracion;
    private Boolean cambioContrasenaRequerido;
    private String fotografiaRuta;
    private String codigoInterno;
    private String observaciones;
    private List<String> roles;
    private List<MembresiaResponse> membresias;

    public UsuarioResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Boolean getBloqueado() { return bloqueado; }
    public void setBloqueado(Boolean bloqueado) { this.bloqueado = bloqueado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
    public LocalDateTime getUltimoAcceso() { return ultimoAcceso; }
    public void setUltimoAcceso(LocalDateTime ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }
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
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
    public Boolean getCambioContrasenaRequerido() { return cambioContrasenaRequerido; }
    public void setCambioContrasenaRequerido(Boolean cambioContrasenaRequerido) { this.cambioContrasenaRequerido = cambioContrasenaRequerido; }
    public String getFotografiaRuta() { return fotografiaRuta; }
    public void setFotografiaRuta(String fotografiaRuta) { this.fotografiaRuta = fotografiaRuta; }
    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public List<MembresiaResponse> getMembresias() { return membresias; }
    public void setMembresias(List<MembresiaResponse> membresias) { this.membresias = membresias; }
}
