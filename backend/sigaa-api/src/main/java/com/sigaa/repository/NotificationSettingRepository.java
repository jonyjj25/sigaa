package com.sigaa.repository;

import com.sigaa.model.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    List<NotificationSetting> findByEventoIdOrderByCanalAsc(Long eventoId);
    Optional<NotificationSetting> findByEventoIdAndCanal(Long eventoId, String canal);
    List<NotificationSetting> findByActivo(Boolean activo);
    List<NotificationSetting> findAllByOrderByCreatedAtDesc();
    List<NotificationSetting> findByCanal(String canal);
    List<NotificationSetting> findByPrioridad(String prioridad);
    List<NotificationSetting> findByFrecuencia(String frecuencia);
    void deleteByEventoId(Long eventoId);
}
