package com.sigaa.service;

import com.sigaa.model.AuditLog;
import com.sigaa.model.Nota;
import com.sigaa.model.NotaHistorial;
import com.sigaa.model.NotaSeguimientoEstado;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.NotaHistorialRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Registra la bitácora funcional y la auditoría global del módulo. */
@Service
public class NotaTrazabilidadService {
    private static final List<String> ACCIONES_SEGUIMIENTO = Stream.concat(
        NotaSeguimientoEstado.acciones().stream(), Stream.of("FIRMAR")).toList();
    private final NotaHistorialRepository historialRepository;
    private final AuditLogRepository auditLogRepository;

    public NotaTrazabilidadService(NotaHistorialRepository historialRepository,
                                   AuditLogRepository auditLogRepository) {
        this.historialRepository = historialRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public void historial(Nota nota, Usuario usuario, String accion,
                          String anterior, String nuevo, String detalle) {
        historial(nota, usuario, accion, anterior, nuevo, detalle, null);
    }

    public void historial(Nota nota, Usuario usuario, String accion,
                          String anterior, String nuevo, String detalle, LocalDateTime fecha) {
        NotaHistorial historial = new NotaHistorial();
        historial.setNota(nota);
        historial.setUsuario(usuario);
        historial.setAccion(accion);
        historial.setEstadoAnterior(anterior);
        historial.setEstadoNuevo(nuevo);
        historial.setDetalle(detalle);
        if (fecha != null) historial.setCreatedAt(fecha);
        historialRepository.save(historial);
    }

    public SeguimientoActual seguimientoActual(Nota nota) {
        return seguimientoActual(nota, historialRepository
            .findFirstByNotaIdAndAccionInOrderByCreatedAtDescIdDesc(nota.getId(), ACCIONES_SEGUIMIENTO)
            .map(List::of).orElseGet(List::of));
    }

    /** El detalle ya consulta el historial completo: reutilizarlo evita otra consulta. */
    public SeguimientoActual seguimientoActual(Nota nota, List<NotaHistorial> historial) {
        NotaHistorial ultimo = historial.stream().filter(evento -> ACCIONES_SEGUIMIENTO.contains(evento.getAccion()))
            .reduce((anterior, siguiente) -> siguiente).orElse(null);
        NotaSeguimientoEstado estado = ultimo != null ? NotaSeguimientoEstado.porAccion(ultimo.getAccion()).orElse(NotaSeguimientoEstado.PENDIENTE_ENTREGA)
            : nota.getFirmadaAt() != null || NotaService.FIRMADA.equals(nota.getEstado()) ? NotaSeguimientoEstado.PENDIENTE_ENTREGA : null;
        return new SeguimientoActual(estado, ultimo);
    }

    public record SeguimientoActual(NotaSeguimientoEstado estado, NotaHistorial evento) {}

    public boolean firmaReceptorRegistrada(Nota nota) {
        return historialRepository.existsByNotaIdAndAccion(nota.getId(), NotaSeguimientoEstado.FIRMADA_RECEPTOR.name());
    }

    /** Una actualización posterior de seguimiento no borra la firma física registrada. */
    public boolean firmaReceptorRegistrada(List<NotaHistorial> historial) {
        return historial.stream().anyMatch(evento -> NotaSeguimientoEstado.FIRMADA_RECEPTOR.name().equals(evento.getAccion()));
    }

    public void audit(String accion, Long notaId, String detalle) {
        AuditLog log = new AuditLog();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        log.setUsuario(authentication == null ? "sistema" : authentication.getName());
        log.setAccion(accion);
        log.setEntidad("nota");
        log.setEntidadId(notaId);
        log.setDetalle(detalle);
        auditLogRepository.save(log);
    }
}
