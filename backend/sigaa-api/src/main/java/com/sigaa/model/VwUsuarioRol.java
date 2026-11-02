package com.sigaa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

@Entity
@Immutable
@Subselect("SELECT ROW_NUMBER() OVER() AS id, usuario_id, rol FROM vw_usuario_roles")
@Synchronize({"usuario_roles", "usuario_unidad", "roles"})
public class VwUsuarioRol {

    @Id
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String rol;

    public VwUsuarioRol() {}

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getRol() { return rol; }
}
