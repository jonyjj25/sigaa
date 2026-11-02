package com.sigaa.dto;

import java.util.HashSet;
import java.util.Set;

public class LoginResponse {

    private Long id;
    private String status;
    private String message;
    private String code;
    private String token;
    private String nombre;
    private String correo;
    private String roles;
    private Set<String> permisos = new HashSet<>();
    private boolean requires2fa;
    private String tempToken;
    private String fotografiaRuta;
    private boolean cambioContrasenaRequerido;

    public LoginResponse() {
    }

    public LoginResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    public Set<String> getPermisos() { return permisos; }
    public void setPermisos(Set<String> permisos) { this.permisos = permisos; }
    public boolean isRequires2fa() { return requires2fa; }
    public void setRequires2fa(boolean requires2fa) { this.requires2fa = requires2fa; }
    public String getTempToken() { return tempToken; }
    public void setTempToken(String tempToken) { this.tempToken = tempToken; }
    public String getFotografiaRuta() { return fotografiaRuta; }
    public void setFotografiaRuta(String fotografiaRuta) { this.fotografiaRuta = fotografiaRuta; }
    public boolean isCambioContrasenaRequerido() { return cambioContrasenaRequerido; }
    public void setCambioContrasenaRequerido(boolean value) { this.cambioContrasenaRequerido = value; }
}
