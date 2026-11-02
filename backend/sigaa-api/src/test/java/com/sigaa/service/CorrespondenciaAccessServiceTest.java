package com.sigaa.service;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrespondenciaAccessServiceTest {
    final NotaUsuarioActualService session = mock(NotaUsuarioActualService.class);
    final NotaRepository notas = mock(NotaRepository.class);
    final CorrespondenciaAccessService acceso = new CorrespondenciaAccessService(session, notas, mock(MemoRepository.class));

    Usuario usuario(long id) { Usuario user = new Usuario(); user.setId(id); return user; }

    @Test void admiteParticipantesYAdministradoresPeroDeniegaOtrosUsuarios() {
        Nota nota = new Nota();
        nota.setCreadoPor(usuario(1)); nota.setSeguimientoUsuario(usuario(2)); nota.setFirmante(usuario(3));
        assertThat(acceso.puedeVer(nota, usuario(1))).isTrue();
        assertThat(acceso.puedeVer(nota, usuario(2))).isTrue();
        assertThat(acceso.puedeVer(nota, usuario(3))).isTrue();
        assertThat(acceso.puedeVer(nota, usuario(4))).isFalse();
        for (String nombre : Set.of("Director", "superadmin", "Secretaria")) {
            Usuario admin = usuario(4); admin.setRoles(Set.of(new Role(nombre, "")));
            assertThat(acceso.puedeVer(nota, admin)).isTrue();
            admin.setBloqueado(true);
            assertThat(acceso.puedeVer(nota, admin)).isFalse();
        }
        Usuario inactivo = usuario(1); inactivo.setActivo(false);
        assertThat(acceso.puedeVer(nota, inactivo)).isFalse();
        assertThat(acceso.puedeVer(nota, null)).isFalse();
        for (String nombre : Set.of("Subdirector", "Administrador")) {
            Usuario limitado = usuario(4); limitado.setRoles(Set.of(new Role(nombre, "")));
            assertThat(acceso.puedeVer(nota, limitado)).isFalse();
            limitado.setId(2L);
            assertThat(acceso.puedeVer(nota, limitado)).isTrue();
        }
    }

    @Test void memosSoloAdmitenCreadorResponsableYAdministradores() {
        Memo memo = new Memo(); memo.setCreadoPor(usuario(1)); memo.setSeguimientoUsuario(usuario(2));
        assertThat(acceso.puedeVer(memo, usuario(1))).isTrue();
        assertThat(acceso.puedeVer(memo, usuario(2))).isTrue();
        assertThat(acceso.puedeVer(memo, usuario(3))).isFalse();
    }

    @Test void usaUsuarioDeSesionYDeniegaNotasInexistentes() {
        Nota nota = new Nota(); nota.setCreadoPor(usuario(1));
        when(session.actual()).thenReturn(usuario(2)); when(notas.findById(10L)).thenReturn(Optional.of(nota));
        assertThat(acceso.nota(10L)).isFalse();
        assertThatThrownBy(() -> acceso.exigirNota(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(session.actual()).thenReturn(usuario(1));
        assertThat(acceso.nota(10L)).isTrue();
        assertThat(acceso.nota(11L)).isFalse();
    }
}
