package com.sigaa.repository;

import com.sigaa.model.NotificationGlobalSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationGlobalSettingRepository extends JpaRepository<NotificationGlobalSetting, Long> {
    Optional<NotificationGlobalSetting> findByClave(String clave);
    List<NotificationGlobalSetting> findAllByOrderByClaveAsc();
}
