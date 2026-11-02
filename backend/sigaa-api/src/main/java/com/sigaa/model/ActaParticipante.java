package com.sigaa.model;

import jakarta.persistence.*;

@Entity
@Table(name = "acta_participantes")
public class ActaParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "acta_id", nullable = false)
    private Long actaId;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String apellido = "";

    @Column(length = 200)
    private String area = "";

    @Column(nullable = false)
    private Boolean asistio = false;

    @Column(length = 150)
    private String correo = "";

    @Column(length = 200)
    private String institucion = "";

    @Column(length = 150)
    private String cargo = "";

    public ActaParticipante() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActaId() { return actaId; }
    public void setActaId(Long actaId) { this.actaId = actaId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public Boolean getAsistio() { return asistio; }
    public void setAsistio(Boolean asistio) { this.asistio = asistio; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getInstitucion() { return institucion; }
    public void setInstitucion(String institucion) { this.institucion = institucion; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
}
