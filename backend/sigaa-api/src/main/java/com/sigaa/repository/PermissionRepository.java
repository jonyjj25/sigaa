package com.sigaa.repository;

import com.sigaa.model.Permission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByActivoTrue();
    List<Permission> findByModulo(String modulo);
    List<Permission> findByModuloOrderByAccionAsc(String modulo);
    List<Permission> findAllByOrderByModuloAscAccionAscRecursoAsc();

    Optional<Permission> findByModuloAndAccionAndRecurso(String modulo, String accion, String recurso);

    boolean existsByModuloAndAccionAndRecurso(String modulo, String accion, String recurso);

    @Query("SELECT DISTINCT p.modulo FROM Permission p ORDER BY p.modulo")
    List<String> findDistinctModulos();

    @Query("SELECT DISTINCT p.accion FROM Permission p ORDER BY p.accion")
    List<String> findDistinctAcciones();

    @Query("SELECT DISTINCT p.recurso FROM Permission p ORDER BY p.recurso")
    List<String> findDistinctRecursos();

    @Query("SELECT p FROM Permission p WHERE p.modulo = :modulo AND p.accion = :accion AND p.recurso = :recurso AND p.id <> :excludeId")
    Optional<Permission> findDuplicateExcluding(@Param("modulo") String modulo, @Param("accion") String accion, @Param("recurso") String recurso, @Param("excludeId") Long excludeId);

    long countByActivoTrue();
}
