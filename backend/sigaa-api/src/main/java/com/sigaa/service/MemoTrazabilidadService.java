package com.sigaa.service;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class MemoTrazabilidadService {
    private final MemoHistorialRepository historialRepository;
    private final AuditLogRepository auditLogRepository;

    public MemoTrazabilidadService(MemoHistorialRepository historialRepository, AuditLogRepository auditLogRepository) {
        this.historialRepository = historialRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public void historial(Memo memo, Usuario usuario, String accion, String anterior, String nuevo, String detalle) {
        MemoHistorial item = new MemoHistorial();
        item.setMemo(memo); item.setUsuario(usuario); item.setAccion(accion);
        item.setEstadoAnterior(anterior); item.setEstadoNuevo(nuevo); item.setDetalle(detalle);
        historialRepository.save(item);
    }

    public void audit(String accion, Long memoId, String detalle) {
        AuditLog log = new AuditLog();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        log.setUsuario(auth == null ? "sistema" : auth.getName());
        log.setAccion(accion); log.setEntidad("memo"); log.setEntidadId(memoId); log.setDetalle(detalle);
        auditLogRepository.save(log);
    }
}
