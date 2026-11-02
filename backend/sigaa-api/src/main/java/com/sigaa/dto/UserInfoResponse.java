package com.sigaa.dto;

import java.util.HashSet;
import java.util.Set;

public class UserInfoResponse {

    private Long id;
    private String nombre;
    private String correo;
    private String roles;
    private Set<String> permisos = new HashSet<>();
    private boolean has2fa;
    private boolean cambioContrasenaRequerido;
    private String fotografiaRuta;

    public UserInfoResponse() {
    }

    public UserInfoResponse(String nombre, String correo, String roles, Set<String> permisos) {
        this.nombre = nombre;
        this.correo = correo;
        this.roles = roles;
        this.permisos = permisos;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    public Set<String> getPermisos() { return permisos; }
    public void setPermisos(Set<String> permisos) { this.permisos = permisos; }
    public boolean isHas2fa() { return has2fa; }
    public void setHas2fa(boolean has2fa) { this.has2fa = has2fa; }
    public boolean isCambioContrasenaRequerido() { return cambioContrasenaRequerido; }
    public void setCambioContrasenaRequerido(boolean value) { this.cambioContrasenaRequerido = value; }
    public String getFotografiaRuta() { return fotografiaRuta; }
    public void setFotografiaRuta(String fotografiaRuta) { this.fotografiaRuta = fotografiaRuta; }
}
