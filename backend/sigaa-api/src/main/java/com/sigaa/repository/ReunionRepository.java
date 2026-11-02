package com.sigaa.repository;

import com.sigaa.model.Reunion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReunionRepository extends JpaRepository<Reunion, Long> {

    @Query("SELECT r FROM Reunion r ORDER BY r.fecha DESC, r.horaInicio DESC")
    List<Reunion> findAllOrderByFechaDescHoraDesc();

    @Query("SELECT r FROM Reunion r WHERE r.estado = :estado ORDER BY r.fecha DESC, r.horaInicio DESC")
    List<Reunion> findByEstadoOrderByFechaDescHoraDesc(@Param("estado") String estado);

    List<Reunion> findByTituloContainingIgnoreCaseOrOrganizadorContainingIgnoreCaseOrAreaContainingIgnoreCase(
        String titulo, String organizador, String area);

    List<Reunion> findByFechaBetweenOrderByFechaDesc(LocalDate inicio, LocalDate fin);

    List<Reunion> findByFechaAndEstadoOrderByHoraInicio(LocalDate fecha, String estado);

    Optional<Reunion> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.codigo, 5) AS long)), 0) FROM Reunion r WHERE r.codigo LIKE 'RUN-%'")
    long findMaxCodigoNumber();

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(1397311305, 3)", nativeQuery = true)
    int bloquearGeneracionCodigo();

    long countByEstado(String estado);
    @Query("SELECT r.estado, COUNT(r) FROM Reunion r GROUP BY r.estado")
    List<Object[]> countByEstadoAgrupado();

    long countByFechaBetween(LocalDate inicio, LocalDate fin);

    /**
     * La visibilidad personal de una reunión se concede exclusivamente por la
     * relación normalizada de participantes. Los campos de texto heredados y
     * el organizador son informativos: no se usan como autorización.
     */
    @Query("SELECT DISTINCT r FROM Reunion r JOIN r.participantesUsuarios p " +
           "WHERE p.id = :usuarioId ORDER BY r.fecha DESC, r.horaInicio DESC")
    List<Reunion> findByParticipanteUsuarioIdOrderByFechaDescHoraDesc(@Param("usuarioId") Long usuarioId);

    @Query("SELECT DISTINCT r FROM Reunion r LEFT JOIN r.participantesUsuarios p " +
           "WHERE p.id = :usuarioId OR r.organizadorUsuario.id = :usuarioId " +
           "ORDER BY r.fecha DESC, r.horaInicio DESC")
    List<Reunion> findByAmbitoSubdireccion(@Param("usuarioId") Long usuarioId);

    List<Reunion> findByUnidadResponsableInOrderByFechaDescHoraInicioDesc(java.util.Collection<String> unidades);
}
