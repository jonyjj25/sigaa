package com.sigaa.repository;

import com.sigaa.model.TareaAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface TareaAdjuntoRepository extends JpaRepository<TareaAdjunto, Long> {
    List<TareaAdjunto> findByTareaIdOrderById(Long tareaId);
    List<TareaAdjunto> findByTareaIdInOrderById(Collection<Long> tareaIds);
    void deleteByTareaId(Long tareaId);
}
