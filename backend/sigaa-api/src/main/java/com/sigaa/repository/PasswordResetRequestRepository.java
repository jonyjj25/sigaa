package com.sigaa.repository;

import com.sigaa.model.PasswordResetRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetRequest> findFirstByUsuarioIdAndUsedFalseAndBlockedFalseOrderByCreatedAtDesc(Long usuarioId);

    // La búsqueda inicial no carga entidades que puedan quedar obsoletas mientras se espera el bloqueo.
    List<TokenCandidate> findByCodeVerifiedAtIsNotNullAndUsedFalseAndBlockedFalseAndResetTokenExpiresAtAfter(LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PasswordResetRequest r WHERE r.id = :id")
    Optional<PasswordResetRequest> findByIdForUpdate(@Param("id") Long id);

    long countByUsuarioIdAndCreatedAtAfter(Long usuarioId, LocalDateTime createdAt);
    long countByRequestIpAndCreatedAtAfter(String requestIp, LocalDateTime createdAt);

    interface TokenCandidate {
        Long getId();
        String getResetTokenHash();
    }
}
