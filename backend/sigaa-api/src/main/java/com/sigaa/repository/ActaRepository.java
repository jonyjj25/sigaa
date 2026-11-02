package com.sigaa.repository;

import com.sigaa.model.Acta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

public interface ActaRepository extends JpaRepository<Acta, Long> {
    List<Acta> findAllByOrderByFechaDesc();
    long countBySesionId(Long sesionId);
    List<Acta> findBySesionId(Long sesionId);
    boolean existsByCodigo(String codigo);
    @Query("SELECT MAX(a.id) FROM Acta a")
    Optional<Long> findMaxId();

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(1397311305, 2)", nativeQuery = true)
    int bloquearGeneracionCodigo();

    @Query(value = """
        SELECT GREATEST(COALESCE(MAX(id), 0), COALESCE(MAX(
          CASE WHEN codigo ~ '^ACT-[0-9]{4}-[0-9]+$' THEN CAST(SPLIT_PART(codigo, '-', 3) AS BIGINT) END), 0))
        FROM actas
        """, nativeQuery = true)
    long ultimoNumeroCodigo();

    @Query("SELECT DISTINCT a FROM Acta a JOIN ActaParticipante p ON p.actaId = a.id " +
           "WHERE LOWER(TRIM(p.correo)) = LOWER(TRIM(:correo)) OR " +
           "LOWER(TRIM(CONCAT(p.nombre, ' ', p.apellido))) = LOWER(TRIM(:nombre)) " +
           "ORDER BY a.fecha DESC")
    List<Acta> findByParticipanteUsuario(@Param("correo") String correo, @Param("nombre") String nombre);

    @Query("SELECT DISTINCT a FROM Acta a LEFT JOIN ActaParticipante p ON p.actaId = a.id " +
           "LEFT JOIN a.sesion s WHERE s.creadoPor = :usuarioId OR " +
           "LOWER(TRIM(p.correo)) = LOWER(TRIM(:correo)) OR " +
           "LOWER(TRIM(CONCAT(p.nombre, ' ', p.apellido))) = LOWER(TRIM(:nombre)) OR " +
           "LOWER(TRIM(a.responsable)) = LOWER(TRIM(:correo)) OR " +
           "LOWER(TRIM(a.responsable)) = LOWER(TRIM(:nombre)) ORDER BY a.fecha DESC")
    List<Acta> findByAmbitoSubdireccion(@Param("usuarioId") Long usuarioId,
                                      @Param("correo") String correo, @Param("nombre") String nombre);
}
