package com.sigaa.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class PasswordResetCodeRequest {
    @JsonAlias("email") private String correo;
    private String code;
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
