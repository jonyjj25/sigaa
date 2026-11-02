package com.sigaa.repository;

import com.sigaa.model.AsistenciaSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AsistenciaSesionRepository extends JpaRepository<AsistenciaSesion, Long> {
    Optional<AsistenciaSesion> findByCodigo(String codigo);
    List<AsistenciaSesion> findAllByOrderByFechaDescCreadoEnDesc();
    List<AsistenciaSesion> findByActivoTrueOrderByFechaDesc();
    List<AsistenciaSesion> findByArchivadoFalseOrderByFechaDescCreadoEnDesc();
    List<AsistenciaSesion> findByArchivadoTrueOrderByFechaDescCreadoEnDesc();
    List<AsistenciaSesion> findByArchivadoFalseAndFechaBefore(LocalDate fecha);
}
