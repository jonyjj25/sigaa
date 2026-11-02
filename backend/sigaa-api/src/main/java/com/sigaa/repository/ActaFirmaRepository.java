package com.sigaa.repository;

import com.sigaa.model.ActaFirma;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActaFirmaRepository extends JpaRepository<ActaFirma, Long> {
    List<ActaFirma> findByActaIdOrderById(Long actaId);
    void deleteByActaId(Long actaId);
}
