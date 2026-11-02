package com.sigaa.repository;

import com.sigaa.model.MemoNumero;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MemoNumeroRepository extends JpaRepository<MemoNumero, Long> {
    List<MemoNumero> findAllByOrderByNumeroAsc();
    boolean existsByNumeroIgnoreCase(String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select numero from MemoNumero numero where numero.id = :id")
    Optional<MemoNumero> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select numero from MemoNumero numero")
    List<MemoNumero> findAllForUpdate();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MemoNumero numero set numero.estado = 'DISPONIBLE', numero.reservadoPor = null, "
        + "numero.reservadoDesde = null, numero.reservadoHasta = null "
        + "where numero.estado = 'RESERVADO' and numero.reservadoHasta <= :ahora")
    int liberarReservasExpiradas(@Param("ahora") LocalDateTime ahora);
}
