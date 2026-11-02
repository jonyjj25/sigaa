package com.sigaa.repository;

import com.sigaa.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    /** Incremento atómico: un logout no puede sobrescribir otra revocación concurrente. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Usuario u SET u.tokenVersion = COALESCE(u.tokenVersion, 0) + 1 WHERE LOWER(u.correo) = LOWER(:correo)")
    int revokeSessionsByCorreo(@Param("correo") String correo);

    interface ResumenDashboard {
        long getTotal();
        long getActivos();
        long getBloqueados();
        long getUsuariosActual();
        long getUsuariosAnterior();
        long getActivosActual();
        long getActivosAnterior();
        long getBloqueadosActual();
        long getBloqueadosAnterior();
    }

    /** Un recorrido de usuarios para los indicadores y sus mismos períodos. */
    @Query("""
        SELECT COUNT(u) AS total,
          COALESCE(SUM(CASE WHEN u.activo = true THEN 1 ELSE 0 END), 0) AS activos,
          COALESCE(SUM(CASE WHEN u.bloqueado = true THEN 1 ELSE 0 END), 0) AS bloqueados,
          COALESCE(SUM(CASE WHEN u.creadoEn >= :actual AND u.creadoEn < :fin THEN 1 ELSE 0 END), 0) AS usuariosActual,
          COALESCE(SUM(CASE WHEN u.creadoEn >= :anterior AND u.creadoEn < :actual THEN 1 ELSE 0 END), 0) AS usuariosAnterior,
          COALESCE(SUM(CASE WHEN u.activo = true AND u.creadoEn >= :actual AND u.creadoEn < :fin THEN 1 ELSE 0 END), 0) AS activosActual,
          COALESCE(SUM(CASE WHEN u.activo = true AND u.creadoEn >= :anterior AND u.creadoEn < :actual THEN 1 ELSE 0 END), 0) AS activosAnterior,
          COALESCE(SUM(CASE WHEN u.bloqueado = true AND u.actualizadoEn >= :actual AND u.actualizadoEn < :fin THEN 1 ELSE 0 END), 0) AS bloqueadosActual,
          COALESCE(SUM(CASE WHEN u.bloqueado = true AND u.actualizadoEn >= :anterior AND u.actualizadoEn < :actual THEN 1 ELSE 0 END), 0) AS bloqueadosAnterior
        FROM Usuario u
        """)
    ResumenDashboard resumirDashboard(@Param("anterior") LocalDateTime anterior,
        @Param("actual") LocalDateTime actual, @Param("fin") LocalDateTime fin);

    @Query("SELECT u.id FROM Usuario u WHERE u.correo = :correo")
    Optional<Long> findIdByCorreo(@Param("correo") String correo);

    Optional<Usuario> findByCorreo(String correo);
    Optional<Usuario> findByCorreoIgnoreCase(String correo);
    boolean existsByCorreo(String correo);
    boolean existsByCorreoAndIdNot(String correo, Long id);
    List<Usuario> findByNombreContainingIgnoreCase(String nombre);
    List<Usuario> findByActivoTrue();
    List<Usuario> findByActivoFalse();
    List<Usuario> findByBloqueadoTrue();

    @Query("SELECT DISTINCT u FROM Usuario u JOIN u.roles r WHERE LOWER(r.nombre) = 'superadmin' AND u.activo = true")
    List<Usuario> findActiveAdministrators();

    @Query("SELECT DISTINCT u FROM Usuario u JOIN u.roles r " +
           "WHERE LOWER(r.nombre) = 'superadmin' AND r.activo = true")
    List<Usuario> findParticipationAdministrators();
    List<Usuario> findByDepartamento(String departamento);
    List<Usuario> findByCargo(String cargo);
    @Query("SELECT DISTINCT u FROM Usuario u LEFT JOIN u.roles r WHERE " +
           "(LOWER(TRIM(u.cargo)) IN ('director', 'directora') " +
           "OR LOWER(TRIM(u.cargo)) LIKE 'director %' " +
           "OR LOWER(TRIM(u.cargo)) LIKE 'directora %' " +
           "OR LOWER(TRIM(r.nombre)) IN ('director', 'directora')) " +
           "AND u.activo = true AND u.bloqueado = false ORDER BY u.id")
    List<Usuario> findActiveDirectors();
    Optional<Usuario> findByDocumentoIdentidad(String documentoIdentidad);
    boolean existsByDocumentoIdentidad(String documentoIdentidad);
    boolean existsByDocumentoIdentidadAndIdNot(String documentoIdentidad, Long id);
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByUsernameIgnoreCase(String username);
    boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByUsernameAndIdNot(String username, Long id);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);
    Optional<Usuario> findByCodigoInterno(String codigoInterno);
    boolean existsByCodigoInterno(String codigoInterno);

    @Query("SELECT u FROM Usuario u WHERE " +
           "LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.correo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.documentoIdentidad) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.cargo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.departamento) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Usuario> buscar(@Param("q") String q);

    @Query(value = "SELECT u.* FROM usuarios u WHERE " +
           "LOWER(TRIM(u.correo)) = LOWER(TRIM(:identidad)) OR " +
           "LOWER(REGEXP_REPLACE(TRIM(CONCAT_WS(' ', u.nombre, NULLIF(u.segundo_nombre, ''), u.apellido)), '\\s+', ' ', 'g')) = " +
           "LOWER(REGEXP_REPLACE(TRIM(:identidad), '\\s+', ' ', 'g'))", nativeQuery = true)
    List<Usuario> findByIdentidadExacta(@Param("identidad") String identidad);

    long countByActivoTrue();
    long countByActivoFalse();
    long countByBloqueadoTrue();
    long countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(LocalDateTime inicio, LocalDateTime fin);
    long countByActivoTrueAndCreadoEnGreaterThanEqualAndCreadoEnLessThan(LocalDateTime inicio, LocalDateTime fin);
    long countByBloqueadoTrueAndActualizadoEnGreaterThanEqualAndActualizadoEnLessThan(LocalDateTime inicio, LocalDateTime fin);

    @Query(value = "SELECT rol_id as rolId, COUNT(DISTINCT usuario_id) as cnt FROM ("
                 + "SELECT ur.rol_id, ur.usuario_id FROM usuario_roles ur "
                 + "UNION ALL "
                 + "SELECT uu.rol_id, uu.usuario_id FROM usuario_unidad uu"
                 + ") combined GROUP BY rol_id", nativeQuery = true)
    List<Object[]> countUsuariosByRolId();

    @Query("SELECT u FROM Usuario u JOIN FETCH u.roles r WHERE r.id = :rolId")
    List<Usuario> findUsuariosByGlobalRolId(@Param("rolId") Long rolId);

    @Query(value = "SELECT DISTINCT u.* FROM usuarios u " +
                   "INNER JOIN usuario_unidad uu ON uu.usuario_id = u.id " +
                   "WHERE uu.rol_id = :rolId", nativeQuery = true)
    List<Usuario> findUsuariosByUnidadRolId(@Param("rolId") Long rolId);
}
