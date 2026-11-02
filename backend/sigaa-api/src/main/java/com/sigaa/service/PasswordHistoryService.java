package com.sigaa.service;

import com.sigaa.model.PasswordHistory;
import com.sigaa.model.Usuario;
import com.sigaa.repository.PasswordHistoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PasswordHistoryService {
    private final PasswordHistoryRepository repository;
    private final SecurityPolicyService policyService;
    private final PasswordEncoder passwordEncoder;

    public PasswordHistoryService(PasswordHistoryRepository repository, SecurityPolicyService policyService,
                                  PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.policyService = policyService;
        this.passwordEncoder = passwordEncoder;
    }

    public void initializePassword(Usuario user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setCambioContrasenaRequerido(policyService.isForceChangeOnFirstLogin());
        setExpiry(user);
    }

    public void changePassword(Usuario user, String rawPassword) {
        int historySize = policyService.getPasswordHistorySize();
        if (passwordEncoder.matches(rawPassword, user.getPassword())
                || repository.findTop100ByUsuarioIdOrderByCreatedAtDesc(user.getId()).stream()
                .limit(historySize).anyMatch(entry -> passwordEncoder.matches(rawPassword, entry.getPasswordHash()))) {
            throw new IllegalArgumentException("No puede reutilizar una de sus últimas " + historySize + " contraseñas");
        }

        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setCambioContrasenaRequerido(false);
        user.setContrasenaTemporalExpiraEn(null);
        user.revokeSessions();
        setExpiry(user);
        remember(user);
    }

    public void remember(Usuario user) {
        if (user.getId() == null || user.getPassword() == null) return;
        PasswordHistory entry = new PasswordHistory();
        entry.setUsuarioId(user.getId());
        entry.setPasswordHash(user.getPassword());
        repository.save(entry);
    }

    private void setExpiry(Usuario user) {
        int days = policyService.getPasswordExpirationDays();
        user.setFechaExpiracion(days > 0 ? LocalDateTime.now().plusDays(days) : null);
    }
}
