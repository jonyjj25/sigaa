package com.sigaa.repository;

import com.sigaa.model.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findTop100ByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);
}
