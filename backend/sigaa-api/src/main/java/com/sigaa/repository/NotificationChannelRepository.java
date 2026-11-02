package com.sigaa.repository;

import com.sigaa.model.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {
    List<NotificationChannel> findAllByOrderByNombreAsc();
    Optional<NotificationChannel> findByNombre(String nombre);
    long countByActivo(Boolean activo);
}
