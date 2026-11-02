package com.sigaa.repository;

import com.sigaa.model.ActaAcuerdo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActaAcuerdoRepository extends JpaRepository<ActaAcuerdo, Long> {
    List<ActaAcuerdo> findByActaIdOrderById(Long actaId);
    void deleteByActaId(Long actaId);
}
