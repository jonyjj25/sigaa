package com.sigaa.repository;

import com.sigaa.model.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TareaRepository extends JpaRepository<Tarea, Long> {
    List<Tarea> findAllByOrderByFechaCreacionDesc();
    List<Tarea> findByProyecto(String proyecto);
    List<Tarea> findByActaId(Long actaId);
    boolean existsByCodigo(String codigo);

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(1397311305, 4)", nativeQuery = true)
    int bloquearGeneracionCodigo();

    @Query(value = """
        SELECT COALESCE(MAX(CAST(SUBSTRING(codigo FROM CHAR_LENGTH(:prefijo) + 1) AS BIGINT)), 0)
        FROM tareas WHERE codigo LIKE CONCAT(:prefijo, '%') AND codigo ~ '^TAR-[0-9]{4}-[0-9]+$'
        """, nativeQuery = true)
    long ultimoNumeroCodigo(@Param("prefijo") String prefijo);
    List<Tarea> findByUnidadesContainingIgnoreCaseOrderByFechaCreacionDesc(String unidad);
    long countByEstado(String estado);
    @Query("SELECT t.estado, COUNT(t) FROM Tarea t GROUP BY t.estado")
    List<Object[]> countByEstadoAgrupado();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Tarea t SET t.estado = 'vencida', t.updatedAt = :actualizadoEn " +
           "WHERE t.fechaLimite < :fechaActual AND t.estado IN ('por_hacer', 'en_progreso')")
    int marcarAbiertasVencidas(@Param("fechaActual") LocalDate fechaActual,
                               @Param("actualizadoEn") LocalDateTime actualizadoEn);

    /** La autorización personal usa únicamente relaciones normalizadas. */
    @Query("SELECT DISTINCT t FROM Tarea t LEFT JOIN t.participantesUsuarios p " +
           "WHERE t.asignadoUsuario.id = :usuarioId OR p.id = :usuarioId " +
           "ORDER BY t.fechaCreacion DESC")
    List<Tarea> findByResponsableOParticipanteUsuarioId(@Param("usuarioId") Long usuarioId);
}
