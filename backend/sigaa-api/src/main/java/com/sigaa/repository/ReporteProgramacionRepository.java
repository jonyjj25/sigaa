package com.sigaa.repository;

import com.sigaa.model.ReporteProgramacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReporteProgramacionRepository extends JpaRepository<ReporteProgramacion, Long> {
    List<ReporteProgramacion> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ReporteProgramacion p where p.id = :id")
    Optional<ReporteProgramacion> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(p) > 0 from ReporteProgramacion p where locate(concat(';', :ruta, ';'), concat(';', p.archivosRuta, ';')) > 0")
    boolean referenciaArchivo(@Param("ruta") String ruta);

    @Query("select p.id from ReporteProgramacion p where p.activo = true and p.proximaEjecucion <= :ahora order by p.proximaEjecucion, p.id")
    List<Long> findPendientesIds(@Param("ahora") LocalDateTime ahora, Pageable pageable);
}
