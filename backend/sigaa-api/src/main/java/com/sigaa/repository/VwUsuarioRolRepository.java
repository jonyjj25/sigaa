package com.sigaa.repository;

import com.sigaa.model.VwUsuarioRol;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VwUsuarioRolRepository extends JpaRepository<VwUsuarioRol, Long> {
    List<VwUsuarioRol> findByUsuarioId(Long usuarioId);

    // ROW_NUMBER() de la entidad no es una identidad persistente. La autorización
    // lee valores escalares y evita reutilizar entidades con numeración desplazada.
    @org.springframework.data.jpa.repository.Query("select v.rol from VwUsuarioRol v where v.usuarioId = :usuarioId")
    List<String> findRoleNamesByUsuarioId(@org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);
}
