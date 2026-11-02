package com.sigaa.repository;

import com.sigaa.model.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {
    List<NotificationHistory> findAllByOrderByCreatedAtDesc();
    List<NotificationHistory> findByEstado(String estado);
    List<NotificationHistory> findByCanal(String canal);
    List<NotificationHistory> findByEventoCodigo(String eventoCodigo);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByEstado(String estado);
    long countByCanal(String canal);

    @Query("SELECT COUNT(h) FROM NotificationHistory h WHERE h.enviadoEn BETWEEN :start AND :end")
    long countEnviadosEntre(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
