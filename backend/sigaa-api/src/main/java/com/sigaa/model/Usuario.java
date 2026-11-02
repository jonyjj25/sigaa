package com.sigaa.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 150)
    private String segundoNombre;

    @Column(nullable = false, length = 150)
    private String apellido = "";

    @Column(nullable = false, length = 150, unique = true)
    private String correo;

    @Column(length = 150)
    private String correoAlterno;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(nullable = false)
    private Boolean bloqueado = false;

    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "documento_identidad", length = 50)
    private String documentoIdentidad;

    @Column(name = "tipo_documento", length = 20)
    private String tipoDocumento = "CEDULA";

    @Column(length = 30)
    private String telefono;

    @Column(length = 30)
    private String celular;

    @Column(length = 150)
    private String cargo;

    @Column(length = 200)
    private String institucion;

    @Column(length = 150)
    private String departamento;

    @Column(length = 255)
    private String direccion;

    @Column(length = 150)
    private String unidadAdministrativa;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;

    @Column(name = "cambio_contrasena_requerido", nullable = false)
    private Boolean cambioContrasenaRequerido = false;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion = 0L;

    @Column(name = "contrasena_temporal_expira_en")
    private LocalDateTime contrasenaTemporalExpiraEn;

    @Column(name = "fotografia_ruta", length = 500)
    private String fotografiaRuta;

    @Column(name = "codigo_interno", length = 50)
    private String codigoInterno;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @ManyToMany(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinTable(
        name = "usuario_roles",
        joinColumns = @jakarta.persistence.JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "rol_id")
    )
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UsuarioUnidad> membresias = new HashSet<>();

    public Usuario() {}

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }

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
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Boolean getBloqueado() { return bloqueado; }
    public void setBloqueado(Boolean bloqueado) { this.bloqueado = bloqueado; }
    public Integer getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(Integer intentosFallidos) { this.intentosFallidos = intentosFallidos; }
    public LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
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
    public boolean isPasswordExpired() { return fechaExpiracion != null && !fechaExpiracion.isAfter(LocalDateTime.now()); }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
    public Boolean getCambioContrasenaRequerido() { return cambioContrasenaRequerido; }
    public void setCambioContrasenaRequerido(Boolean cambioContrasenaRequerido) { this.cambioContrasenaRequerido = cambioContrasenaRequerido; }
    public Long getTokenVersion() { return tokenVersion == null ? 0L : tokenVersion; }
    public void setTokenVersion(Long tokenVersion) { this.tokenVersion = tokenVersion; }
    public void revokeSessions() { this.tokenVersion = getTokenVersion() + 1L; }
    public LocalDateTime getContrasenaTemporalExpiraEn() { return contrasenaTemporalExpiraEn; }
    public void setContrasenaTemporalExpiraEn(LocalDateTime value) { this.contrasenaTemporalExpiraEn = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isTemporaryPasswordExpired() {
        return Boolean.TRUE.equals(cambioContrasenaRequerido)
                && contrasenaTemporalExpiraEn != null
                && !contrasenaTemporalExpiraEn.isAfter(LocalDateTime.now());
    }
    public String getFotografiaRuta() { return fotografiaRuta; }
    public void setFotografiaRuta(String fotografiaRuta) { this.fotografiaRuta = fotografiaRuta; }
    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) {
        this.roles.clear();
        if (roles != null) this.roles.addAll(roles);
    }
    public Set<UsuarioUnidad> getMembresias() { return membresias; }
    public void setMembresias(Set<UsuarioUnidad> membresias) {
        this.membresias.clear();
        if (membresias != null) this.membresias.addAll(membresias);
    }
}
