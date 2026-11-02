package com.sigaa.repository;

import com.sigaa.model.ActaParticipante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActaParticipanteRepository extends JpaRepository<ActaParticipante, Long> {
    List<ActaParticipante> findByActaIdOrderById(Long actaId);
    void deleteByActaId(Long actaId);
}
