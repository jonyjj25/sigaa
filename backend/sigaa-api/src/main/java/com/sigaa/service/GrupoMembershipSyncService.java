package com.sigaa.service;

import com.sigaa.model.GrupoUsuario;
import com.sigaa.model.Usuario;
import com.sigaa.repository.GrupoRepository;
import com.sigaa.repository.GrupoUsuarioRepository;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Mantiene alineadas las unidades asignadas al usuario y sus grupos de trabajo. */
@Service
public class GrupoMembershipSyncService {

    private final GrupoRepository grupos;
    private final GrupoUsuarioRepository miembros;

    public GrupoMembershipSyncService(GrupoRepository grupos, GrupoUsuarioRepository miembros) {
        this.grupos = grupos;
        this.miembros = miembros;
    }

    public void sincronizar(Usuario usuario) {
        if (!ParticipantePolicy.puedeParticipar(usuario) || usuario.getMembresias() == null) return;

        Set<Long> unidades = usuario.getMembresias().stream()
            .map(membresia -> membresia.getUnidad() == null ? null : membresia.getUnidad().getId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (unidades.isEmpty()) return;

        grupos.findByUnidadIdIn(unidades).forEach(grupo -> {
            var existente = miembros.findByGrupoIdAndUsuarioId(grupo.getId(), usuario.getId());
            if (existente.isPresent()) {
                existente.get().setActivo(true);
                return;
            }
            GrupoUsuario miembro = new GrupoUsuario();
            miembro.setGrupo(grupo);
            miembro.setUsuario(usuario);
            miembro.setResponsable(false);
            miembros.save(miembro);
            grupo.getMiembros().add(miembro);
        });
    }
}
