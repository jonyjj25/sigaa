package com.sigaa.repository;

import com.sigaa.model.NotaNumero;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface NotaNumeroRepository extends JpaRepository<NotaNumero, Long> {
    List<NotaNumero> findAllByOrderByNumeroAsc();
    List<NotaNumero> findByEstadoOrderByNumeroAsc(String estado);
    boolean existsByNumeroIgnoreCase(String numero);

    /** Serializa también la creación del primer inventario de un año vacío. */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(115, :anio)", nativeQuery = true)
    int bloquearAnio(@Param("anio") int anio);

    /** Serializa la toma de un consecutivo entre sesiones concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select numero from NotaNumero numero where numero.id = :id")
    Optional<NotaNumero> findByIdForUpdate(@Param("id") Long id);

    /** Bloquea el inventario mientras se decide cuál es el siguiente consecutivo válido. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select numero from NotaNumero numero")
    List<NotaNumero> findAllForUpdate();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotaNumero numero set numero.estado = 'DISPONIBLE', numero.reservadoPor = null, "
        + "numero.reservadoDesde = null, numero.reservadoHasta = null "
        + "where numero.estado = 'RESERVADO' and numero.reservadoHasta <= :ahora")
    int liberarReservasExpiradas(@Param("ahora") java.time.LocalDateTime ahora);
}
