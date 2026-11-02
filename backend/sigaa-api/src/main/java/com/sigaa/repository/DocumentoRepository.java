package com.sigaa.repository;

import com.sigaa.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findAllByOrderByFechaDesc();
    List<Documento> findByAreaIgnoreCaseOrderByFechaDesc(String area);
    List<Documento> findByCategoriaIgnoreCase(String categoria);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Documento> findByCarpetaIdOrderByFechaDesc(Long carpetaId);
    List<Documento> findByCarpetaIdInOrderByFechaDesc(java.util.Collection<Long> carpetaIds);
    Optional<Documento> findByPropietarioUsuarioIdAndCreacionId(Long propietarioId, UUID creacionId);

    // Leer la ruta vigente después de obtener el bloqueo; conservarlo hasta commit/rollback.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Documento d WHERE d.id = :id")
    Optional<Documento> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(1397311306, :clave)", nativeQuery = true)
    int bloquearCreacion(@Param("clave") int clave);
}
