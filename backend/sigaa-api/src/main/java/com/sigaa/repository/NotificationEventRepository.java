package com.sigaa.repository;

import com.sigaa.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {
    List<NotificationEvent> findAllByOrderByNombreAsc();
    Optional<NotificationEvent> findByCodigo(String codigo);
    List<NotificationEvent> findByCategoriaOrderByNombreAsc(String categoria);
    List<NotificationEvent> findByActivo(Boolean activo);
}
