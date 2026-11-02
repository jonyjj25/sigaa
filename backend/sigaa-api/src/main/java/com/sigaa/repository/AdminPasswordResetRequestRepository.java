package com.sigaa.repository;

import com.sigaa.model.AdminPasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminPasswordResetRequestRepository extends JpaRepository<AdminPasswordResetRequest, Long> {
    Optional<AdminPasswordResetRequest> findByUsuarioIdAndStatus(Long userId, String status);
    List<AdminPasswordResetRequest> findByStatusOrderByRequestedAtDesc(String status);
    long countByRequestIpAndRequestedAtAfter(String requestIp, LocalDateTime after);
}
