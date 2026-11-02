package com.sigaa.repository;

import com.sigaa.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    List<NotificationTemplate> findAllByOrderByNombreAsc();
    List<NotificationTemplate> findByActivo(Boolean activo);
    List<NotificationTemplate> findByEventoId(Long eventoId);
}
