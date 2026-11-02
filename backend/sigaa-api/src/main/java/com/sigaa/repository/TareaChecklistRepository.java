package com.sigaa.repository;

import com.sigaa.model.TareaChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TareaChecklistRepository extends JpaRepository<TareaChecklist, Long> {
    List<TareaChecklist> findByTareaIdOrderById(Long tareaId);
    void deleteByTareaId(Long tareaId);
}
