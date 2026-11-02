package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "carpeta_usuarios", uniqueConstraints = @UniqueConstraint(columnNames = {"carpeta_id", "usuario_id"}))
public class CarpetaUsuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "carpeta_id", nullable = false) private Carpeta carpeta;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false) private Usuario usuario;
    @Column(nullable = false) private LocalDateTime creadoEn = LocalDateTime.now();
    public Long getId(){ return id; } public Carpeta getCarpeta(){ return carpeta; } public void setCarpeta(Carpeta v){ carpeta=v; }
    public Usuario getUsuario(){ return usuario; } public void setUsuario(Usuario v){ usuario=v; }
}
