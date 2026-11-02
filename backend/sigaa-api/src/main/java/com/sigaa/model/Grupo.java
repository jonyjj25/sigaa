package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity @Table(name = "grupos")
public class Grupo {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, length = 150) private String nombre;
  @Column(length = 1000) private String descripcion;
  @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "unidad_id", nullable = false) private Unidad unidad;
  @Column(nullable = false, length = 80) private String tipo;
  @Column(length = 80) private String icono;
  @Column(length = 20) private String color;
  @Column(nullable = false) private Boolean activo = true;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "creado_por_id") private Usuario creadoPor;
  @Column(name = "creado_en", updatable = false) private LocalDateTime creadoEn;
  @Column(name = "actualizado_en") private LocalDateTime actualizadoEn;
  @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true) private Set<GrupoUsuario> miembros = new HashSet<>();
  @ManyToMany @JoinTable(name="grupo_permisos", joinColumns=@JoinColumn(name="grupo_id"), inverseJoinColumns=@JoinColumn(name="permiso_id")) private Set<Permission> permisos = new HashSet<>();
  @PrePersist void crear(){ creadoEn=LocalDateTime.now(); actualizadoEn=creadoEn; } @PreUpdate void actualizar(){ actualizadoEn=LocalDateTime.now(); }
  public Long getId(){return id;} public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;} public String getDescripcion(){return descripcion;} public void setDescripcion(String v){descripcion=v;} public Unidad getUnidad(){return unidad;} public void setUnidad(Unidad v){unidad=v;} public String getTipo(){return tipo;} public void setTipo(String v){tipo=v;} public String getIcono(){return icono;} public void setIcono(String v){icono=v;} public String getColor(){return color;} public void setColor(String v){color=v;} public Boolean getActivo(){return activo;} public void setActivo(Boolean v){activo=v;} public Usuario getCreadoPor(){return creadoPor;} public void setCreadoPor(Usuario v){creadoPor=v;} public LocalDateTime getCreadoEn(){return creadoEn;} public LocalDateTime getActualizadoEn(){return actualizadoEn;} public Set<GrupoUsuario> getMiembros(){return miembros;} public Set<Permission> getPermisos(){return permisos;} public void setPermisos(Set<Permission> v){permisos=v;}
}
