package com.sigaa.repository;

import com.sigaa.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    Optional<UserPreferences> findByUsuarioId(Long usuarioId);
    boolean existsByUsuarioId(Long usuarioId);
}
