package com.sigaa.repository;

import com.sigaa.model.ParameterHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterHistoryRepository extends JpaRepository<ParameterHistory, Long> {

    List<ParameterHistory> findByParametroIdOrderByFechaDesc(Long parametroId);

    List<ParameterHistory> findAllByOrderByFechaDesc();

    List<ParameterHistory> findByClaveOrderByFechaDesc(String clave);
}
