package com.sigaa.repository;

import com.sigaa.model.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    List<NotificationRecipient> findByEventoIdOrderByRolNombreAsc(Long eventoId);
    List<NotificationRecipient> findByEventoIdAndRolId(Long eventoId, Long rolId);
    void deleteByEventoId(Long eventoId);
}
