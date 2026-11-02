package com.sigaa.service;

import com.sigaa.model.Grupo;
import com.sigaa.model.GrupoUsuario;
import com.sigaa.model.Unidad;
import com.sigaa.model.Usuario;
import com.sigaa.model.UsuarioUnidad;
import com.sigaa.repository.GrupoRepository;
import com.sigaa.repository.GrupoUsuarioRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GrupoMembershipSyncServiceTest {

    @Test
    void agregaElUsuarioATodosLosGruposDeSuUnidad() {
        GrupoRepository grupos = mock(GrupoRepository.class);
        GrupoUsuarioRepository miembros = mock(GrupoUsuarioRepository.class);
        GrupoMembershipSyncService service = new GrupoMembershipSyncService(grupos, miembros);

        Unidad unidad = new Unidad(); unidad.setId(7L);
        Usuario usuario = new Usuario(); usuario.setId(11L);
        UsuarioUnidad membresia = new UsuarioUnidad(); membresia.setUsuario(usuario); membresia.setUnidad(unidad);
        usuario.setMembresias(new HashSet<>(List.of(membresia)));
        Grupo grupo = mock(Grupo.class);
        when(grupo.getId()).thenReturn(21L);
        when(grupo.getMiembros()).thenReturn(new HashSet<>());
        when(grupos.findByUnidadIdIn(java.util.Set.of(7L))).thenReturn(List.of(grupo));
        when(miembros.findByGrupoIdAndUsuarioId(21L, 11L)).thenReturn(Optional.empty());

        service.sincronizar(usuario);

        ArgumentCaptor<GrupoUsuario> captor = ArgumentCaptor.forClass(GrupoUsuario.class);
        verify(miembros).save(captor.capture());
        assertThat(captor.getValue().getUsuario()).isSameAs(usuario);
        assertThat(captor.getValue().getGrupo()).isSameAs(grupo);
        assertThat(captor.getValue().getActivo()).isTrue();
    }

    @Test
    void reactivaLaMembresiaExistenteSinDuplicarla() {
        GrupoRepository grupos = mock(GrupoRepository.class);
        GrupoUsuarioRepository miembros = mock(GrupoUsuarioRepository.class);
        GrupoMembershipSyncService service = new GrupoMembershipSyncService(grupos, miembros);
        Unidad unidad = new Unidad(); unidad.setId(7L);
        Usuario usuario = new Usuario(); usuario.setId(11L);
        UsuarioUnidad membresia = new UsuarioUnidad(); membresia.setUsuario(usuario); membresia.setUnidad(unidad);
        usuario.setMembresias(new HashSet<>(List.of(membresia)));
        Grupo grupo = mock(Grupo.class); when(grupo.getId()).thenReturn(21L);
        GrupoUsuario existente = new GrupoUsuario(); existente.setActivo(false);
        when(grupos.findByUnidadIdIn(java.util.Set.of(7L))).thenReturn(List.of(grupo));
        when(miembros.findByGrupoIdAndUsuarioId(21L, 11L)).thenReturn(Optional.of(existente));

        service.sincronizar(usuario);

        assertThat(existente.getActivo()).isTrue();
        verify(miembros, never()).save(any());
    }
}
