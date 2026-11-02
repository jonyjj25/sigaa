package com.sigaa.repository;

import com.sigaa.model.Usuario2fa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Usuario2faRepository extends JpaRepository<Usuario2fa, Long> {
    Optional<Usuario2fa> findByUsuarioId(Long usuarioId);
}
