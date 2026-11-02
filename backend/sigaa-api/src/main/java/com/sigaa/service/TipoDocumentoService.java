package com.sigaa.service;

import com.sigaa.dto.TipoDocumentoRequest;
import com.sigaa.dto.TipoDocumentoResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.model.TipoDocumento;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.TipoDocumentoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TipoDocumentoService {

    private final TipoDocumentoRepository repository;
    private final AuditLogRepository auditLogRepository;

    public TipoDocumentoService(TipoDocumentoRepository repository, AuditLogRepository auditLogRepository) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
    }

    public List<TipoDocumentoResponse> listarTodos() {
        return repository.findAllByOrderByNombreAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<TipoDocumentoResponse> listarActivos() {
        return repository.findByActivoTrueOrderByNombreAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<TipoDocumentoResponse> listarInactivos() {
        return repository.findByActivoFalseOrderByNombreAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<TipoDocumentoResponse> buscar(String termino) {
        return repository
            .findByNombreContainingIgnoreCaseOrCodigoContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
                termino, termino, termino)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public TipoDocumentoResponse obtenerPorId(Long id) {
        TipoDocumento entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Tipo de documento no encontrado con id: " + id));
        return toResponse(entity);
    }

    @Transactional
    public TipoDocumentoResponse crear(TipoDocumentoRequest request) {
        if (repository.existsByCodigo(request.getCodigo().trim().toUpperCase())) {
            throw new IllegalArgumentException("Ya existe un tipo de documento con el código: " + request.getCodigo());
        }
        if (repository.existsByNombre(request.getNombre().trim())) {
            throw new IllegalArgumentException("Ya existe un tipo de documento con el nombre: " + request.getNombre());
        }

        TipoDocumento entity = new TipoDocumento();
        entity.setCodigo(request.getCodigo().trim().toUpperCase());
        entity.setNombre(request.getNombre().trim());
        entity.setDescripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        entity.setActivo(request.getActivo() != null ? request.getActivo() : true);

        TipoDocumento saved = repository.save(entity);
        logAudit("CREAR", "TipoDocumento", saved.getId(),
            "Tipo de documento creado: [" + saved.getCodigo() + "] " + saved.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public TipoDocumentoResponse actualizar(Long id, TipoDocumentoRequest request) {
        TipoDocumento entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Tipo de documento no encontrado con id: " + id));

        if (request.getCodigo() != null) {
            String nuevoCodigo = request.getCodigo().trim().toUpperCase();
            if (repository.existsByCodigoAndIdNot(nuevoCodigo, id)) {
                throw new IllegalArgumentException("Ya existe otro tipo de documento con el código: " + nuevoCodigo);
            }
            entity.setCodigo(nuevoCodigo);
        }

        if (request.getNombre() != null) {
            String nuevoNombre = request.getNombre().trim();
            if (repository.existsByNombreAndIdNot(nuevoNombre, id)) {
                throw new IllegalArgumentException("Ya existe otro tipo de documento con el nombre: " + nuevoNombre);
            }
            entity.setNombre(nuevoNombre);
        }

        if (request.getDescripcion() != null) {
            entity.setDescripcion(request.getDescripcion().trim());
        }

        if (request.getActivo() != null) {
            entity.setActivo(request.getActivo());
        }

        TipoDocumento saved = repository.save(entity);
        logAudit("ACTUALIZAR", "TipoDocumento", saved.getId(),
            "Tipo de documento actualizado: [" + saved.getCodigo() + "] " + saved.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public TipoDocumentoResponse toggleActivo(Long id) {
        TipoDocumento entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Tipo de documento no encontrado con id: " + id));
        entity.setActivo(!entity.getActivo());
        TipoDocumento saved = repository.save(entity);
        logAudit("ACTUALIZAR", "TipoDocumento", saved.getId(),
            "Tipo de documento " + (saved.getActivo() ? "activado" : "desactivado") + ": [" + saved.getCodigo() + "] " + saved.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        TipoDocumento entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Tipo de documento no encontrado con id: " + id));
        repository.delete(entity);
        logAudit("ELIMINAR", "TipoDocumento", id,
            "Tipo de documento eliminado: [" + entity.getCodigo() + "] " + entity.getNombre());
    }

    private TipoDocumentoResponse toResponse(TipoDocumento entity) {
        TipoDocumentoResponse resp = new TipoDocumentoResponse();
        resp.setId(entity.getId());
        resp.setCodigo(entity.getCodigo());
        resp.setNombre(entity.getNombre());
        resp.setDescripcion(entity.getDescripcion());
        resp.setActivo(entity.getActivo());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }

    private void logAudit(String accion, String entidad, Long entidadId, String detalle) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String usuario = auth != null ? auth.getName() : "sistema";
            AuditLog log = new AuditLog();
            log.setUsuario(usuario);
            log.setAccion(accion);
            log.setEntidad(entidad);
            log.setEntidadId(entidadId);
            log.setDetalle(detalle);
            log.setFecha(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception ignored) {
        }
    }
}
