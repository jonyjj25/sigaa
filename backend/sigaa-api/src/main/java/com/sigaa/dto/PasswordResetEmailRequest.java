package com.sigaa.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class PasswordResetEmailRequest {
    @JsonAlias("email") private String correo;
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
}
