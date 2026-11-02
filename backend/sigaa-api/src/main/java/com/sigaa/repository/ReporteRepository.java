package com.sigaa.repository;

import com.sigaa.model.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {
    List<Reporte> findAllByOrderByFechaDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reporte r where r.id = :id")
    Optional<Reporte> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(r) > 0 from Reporte r where locate(concat(';', :ruta, ';'), concat(';', coalesce(r.archivosRuta, ''), ';')) > 0")
    boolean referenciaArchivo(@Param("ruta") String ruta);
}
