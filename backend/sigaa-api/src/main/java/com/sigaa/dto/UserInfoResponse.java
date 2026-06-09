package com.sigaa.dto;

public class UserInfoResponse {

    private String nombre;
    private String correo;
    private String roles;

    public UserInfoResponse() {
    }

    public UserInfoResponse(String nombre, String correo, String roles) {
        this.nombre = nombre;
        this.correo = correo;
        this.roles = roles;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }
}
