package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "grupo_mensajes")
public class GrupoMensaje {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "grupo_id", nullable = false) private Grupo grupo;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "autor_id", nullable = false) private Usuario autor;
  @Column(nullable = false, length = 2000) private String contenido;
  @Column(name = "creado_en", nullable = false, updatable = false) private LocalDateTime creadoEn;
  @Column(nullable = false) private boolean eliminado = false;
  @PrePersist void prePersist(){ creadoEn = LocalDateTime.now(); }
  public Long getId(){ return id; } public Grupo getGrupo(){ return grupo; } public void setGrupo(Grupo v){grupo=v;}
  public Usuario getAutor(){return autor;} public void setAutor(Usuario v){autor=v;} public String getContenido(){return contenido;} public void setContenido(String v){contenido=v;}
  public LocalDateTime getCreadoEn(){return creadoEn;} public boolean isEliminado(){return eliminado;} public void setEliminado(boolean v){eliminado=v;}
}
