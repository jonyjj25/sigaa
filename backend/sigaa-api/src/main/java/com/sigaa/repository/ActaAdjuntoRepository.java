package com.sigaa.repository;

import com.sigaa.model.ActaAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActaAdjuntoRepository extends JpaRepository<ActaAdjunto, Long> {
    List<ActaAdjunto> findByActaIdOrderById(Long actaId);
    void deleteByActaId(Long actaId);
}
