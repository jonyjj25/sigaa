package com.sigaa.service;

import com.sigaa.model.Usuario;
import com.sigaa.repository.PasswordHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordHistorySessionRevocationTest {
    @Test
    void passwordChangeInvalidatesAllPreviousTokens() {
        PasswordHistoryRepository repository = mock(PasswordHistoryRepository.class);
        SecurityPolicyService policies = mock(SecurityPolicyService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordHistoryService service = new PasswordHistoryService(repository, policies, encoder);
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setPassword("hash-anterior");
        usuario.setTokenVersion(7L);

        when(policies.getPasswordHistorySize()).thenReturn(5);
        when(policies.getPasswordExpirationDays()).thenReturn(0);
        when(repository.findTop100ByUsuarioIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(encoder.matches("nueva-clave", "hash-anterior")).thenReturn(false);
        when(encoder.encode("nueva-clave")).thenReturn("hash-nuevo");

        service.changePassword(usuario, "nueva-clave");

        assertThat(usuario.getTokenVersion()).isEqualTo(8L);
        assertThat(usuario.getPassword()).isEqualTo("hash-nuevo");
    }
}
