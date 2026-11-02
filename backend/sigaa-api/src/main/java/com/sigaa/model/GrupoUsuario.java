package com.sigaa.model;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="grupo_usuarios") public class GrupoUsuario {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="grupo_id",nullable=false) private Grupo grupo;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="usuario_id",nullable=false) private Usuario usuario;
 @Column(name="es_responsable",nullable=false) private Boolean responsable=false;
 @Column(name="fecha_asignacion",nullable=false) private LocalDateTime fechaAsignacion;
 @Column(nullable=false) private Boolean activo=true;
 @PrePersist void crear(){fechaAsignacion=LocalDateTime.now();}
 public Long getId(){return id;} public Grupo getGrupo(){return grupo;} public void setGrupo(Grupo v){grupo=v;} public Usuario getUsuario(){return usuario;} public void setUsuario(Usuario v){usuario=v;} public Boolean getResponsable(){return responsable;} public void setResponsable(Boolean v){responsable=v;} public LocalDateTime getFechaAsignacion(){return fechaAsignacion;} public Boolean getActivo(){return activo;} public void setActivo(Boolean v){activo=v;}
}
