package com.sigaa.service;

import com.sigaa.model.Nota;
import com.sigaa.model.Memo;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaRepository;
import com.sigaa.repository.MemoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

/** El permiso funcional nunca sustituye la autorización sobre la correspondencia. */
@Service
@Transactional(readOnly = true)
public class CorrespondenciaAccessService {
    private final NotaUsuarioActualService session;
    private final NotaRepository notas;
    private final MemoRepository memos;

    public CorrespondenciaAccessService(NotaUsuarioActualService session, NotaRepository notas, MemoRepository memos) {
        this.session = session; this.notas = notas; this.memos = memos;
    }

    public boolean administrador(Usuario usuario) {
        return usable(usuario) && usuario.getRoles() != null && usuario.getRoles().stream()
            .anyMatch(role -> Boolean.TRUE.equals(role.getActivo()) && (InstitutionalRoles.operational(role.getNombre())
                || "Secretaria".equals(InstitutionalRoles.canonical(role.getNombre()))));
    }

    public boolean puedeVer(Nota nota, Usuario usuario) {
        return nota != null && usable(usuario) && (administrador(usuario)
            || mismo(nota.getCreadoPor(), usuario) || mismo(nota.getSeguimientoUsuario(), usuario)
            || mismo(nota.getFirmante(), usuario));
    }

    public boolean puedeVer(Memo memo, Usuario usuario) {
        return memo != null && usable(usuario) && (administrador(usuario)
            || mismo(memo.getCreadoPor(), usuario) || mismo(memo.getSeguimientoUsuario(), usuario));
    }

    public boolean nota(Long id) {
        Usuario usuario = session.actual();
        return notas.findById(id).map(item -> puedeVer(item, usuario)).orElse(false);
    }

    public boolean memo(Long id) {
        Usuario usuario = session.actual();
        return memos.findById(id).map(item -> puedeVer(item, usuario)).orElse(false);
    }

    public void exigirNota(Long id) {
        if (!nota(id)) throw new AccessDeniedException("Sin acceso a esta correspondencia.");
    }

    private boolean usable(Usuario usuario) {
        return usuario != null && usuario.getId() != null && Boolean.TRUE.equals(usuario.getActivo())
            && !Boolean.TRUE.equals(usuario.getBloqueado());
    }

    private boolean mismo(Usuario participante, Usuario usuario) {
        return participante != null && usuario.getId().equals(participante.getId());
    }
}
