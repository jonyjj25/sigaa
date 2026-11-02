package com.sigaa.repository;

import com.sigaa.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);
    long countByUsuarioIdAndLeidaFalse(Long usuarioId);
    List<Notificacion> findByUsuarioIdAndLeidaFalseOrderByCreatedAtDesc(Long usuarioId);
    List<Notificacion> findByUsuarioIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long usuarioId, LocalDateTime createdAt);
}
