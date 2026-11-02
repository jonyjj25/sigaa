package com.sigaa.dto;

import java.util.List;

public class RoleResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private Boolean sistema;
    private List<PermissionResponse> permisos;
    private Integer usuarios;

    public RoleResponse() {}

    public RoleResponse(Long id, String nombre, String descripcion, Boolean activo, List<PermissionResponse> permisos) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
        this.permisos = permisos;
        this.usuarios = 0;
    }

    public RoleResponse(Long id, String nombre, String descripcion, Boolean activo, List<PermissionResponse> permisos, Integer usuarios) {
        this(id, nombre, descripcion, activo, false, permisos, usuarios);
    }

    public RoleResponse(Long id, String nombre, String descripcion, Boolean activo, Boolean sistema,
                        List<PermissionResponse> permisos, Integer usuarios) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
        this.sistema = sistema;
        this.permisos = permisos;
        this.usuarios = usuarios;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Boolean getSistema() { return sistema; }
    public void setSistema(Boolean sistema) { this.sistema = sistema; }
    public List<PermissionResponse> getPermisos() { return permisos; }
    public void setPermisos(List<PermissionResponse> permisos) { this.permisos = permisos; }
    public Integer getUsuarios() { return usuarios; }
    public void setUsuarios(Integer usuarios) { this.usuarios = usuarios; }
}
