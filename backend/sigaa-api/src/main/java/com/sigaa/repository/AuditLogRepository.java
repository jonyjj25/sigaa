package com.sigaa.repository;
import com.sigaa.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findAllByOrderByFechaDesc();
    List<AuditLog> findTop200ByOrderByFechaDesc();
    List<AuditLog> findByEntidadAndEntidadIdOrderByFechaDesc(String entidad, Long entidadId);
    List<AuditLog> findByEntidadIdOrderByFechaDesc(Long entidadId);
    List<AuditLog> findTop10ByOrderByFechaDesc();
    long countByFechaGreaterThanEqual(LocalDateTime desde);

    @Query("SELECT COUNT(DISTINCT a.usuario) FROM AuditLog a WHERE a.usuario IS NOT NULL")
    long countDistinctUsuarios();

    @Query("""
            SELECT COUNT(a) FROM AuditLog a
            WHERE UPPER(COALESCE(a.accion, '')) LIKE '%ELIMINAR%'
               OR UPPER(COALESCE(a.accion, '')) LIKE '%BLOQUEAR%'
               OR UPPER(COALESCE(a.accion, '')) LIKE '%RESTABLECER%'
               OR UPPER(COALESCE(a.accion, '')) LIKE '%CAMBIO%OBLIGATORIO%'
            """)
    long countEventosCriticos();

    @Query("SELECT DISTINCT a.accion FROM AuditLog a WHERE a.accion IS NOT NULL ORDER BY a.accion")
    List<String> findDistinctAcciones();

    @Query("SELECT DISTINCT a.entidad FROM AuditLog a WHERE a.entidad IS NOT NULL ORDER BY a.entidad")
    List<String> findDistinctEntidades();

    @Query("SELECT MONTH(a.fecha), COUNT(a) FROM AuditLog a WHERE a.fecha >= :desde GROUP BY MONTH(a.fecha)")
    List<Object[]> countByMonthSince(@Param("desde") LocalDateTime desde);
}
