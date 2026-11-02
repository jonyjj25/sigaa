package com.sigaa.repository;

import com.sigaa.model.UsuarioUnidad;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioUnidadRepository extends JpaRepository<UsuarioUnidad, Long> {
    List<UsuarioUnidad> findByUsuarioId(Long usuarioId);

    /**
     * La creación de grupos necesita conocer el jefe y los miembros de la
     * unidad. Cargarlos aquí evita acceder a proxies LAZY fuera de la
     * transacción de repositorio y devolver un 500 al guardar el grupo.
     */
    @EntityGraph(attributePaths = {"usuario", "usuario.roles", "rol"})
    List<UsuarioUnidad> findByUnidadId(Long unidadId);
    @EntityGraph(attributePaths = {"unidad", "rol"})
    List<UsuarioUnidad> findByUsuarioIdIn(java.util.Collection<Long> ids);

    void deleteByUsuarioId(Long usuarioId);
}
