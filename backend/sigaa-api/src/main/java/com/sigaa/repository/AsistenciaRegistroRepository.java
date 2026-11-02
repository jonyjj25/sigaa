package com.sigaa.repository;

import com.sigaa.model.AsistenciaRegistro;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenciaRegistroRepository extends JpaRepository<AsistenciaRegistro, Long> {
    List<AsistenciaRegistro> findByUsuarioIdAndSesionIsNotNullOrderByFechaDesc(Long usuarioId);
    @EntityGraph(attributePaths = {"usuario", "usuario.roles"})
    List<AsistenciaRegistro> findBySesionIdOrderByHoraEntradaAsc(Long sesionId);
    long countBySesionId(Long sesionId);
    boolean existsByUsuarioIdAndSesionId(Long usuarioId, Long sesionId);
}
