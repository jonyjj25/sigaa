package com.sigaa.repository;

import com.sigaa.model.AsistenciaInvitado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AsistenciaInvitadoRepository extends JpaRepository<AsistenciaInvitado, Long> {
    List<AsistenciaInvitado> findBySesionIdOrderByHoraRegistroDesc(Long sesionId);
    long countBySesionId(Long sesionId);
    boolean existsBySesionIdAndCorreoIgnoreCase(Long sesionId, String correo);
}
