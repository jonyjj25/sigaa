package com.sigaa.service;

import com.sigaa.dto.OrgUnitRequest;
import com.sigaa.dto.OrgUnitResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.model.OrgUnit;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.OrgUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;
    private final AuditLogRepository auditLogRepository;

    public OrgUnitService(OrgUnitRepository orgUnitRepository, AuditLogRepository auditLogRepository) {
        this.orgUnitRepository = orgUnitRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public List<OrgUnitResponse> listar() {
        return orgUnitRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrgUnitResponse> listarActivos() {
        return orgUnitRepository.findByActivoTrueOrderByOrdenAscNombreAsc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrgUnitResponse> listarRaices() {
        return orgUnitRepository.findByPadreIsNullOrderByOrdenAscNombreAsc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrgUnitResponse> listarPorNivel(int nivel) {
        return orgUnitRepository.findByNivelOrderByOrdenAscNombreAsc(nivel).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrgUnitResponse> listarPorTipo(String tipo) {
        return orgUnitRepository.findByTipoOrderByOrdenAscNombreAsc(tipo).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrgUnitResponse> listarHijos(Long padreId) {
        return orgUnitRepository.findByPadreIdOrderByOrdenAscNombreAsc(padreId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrgUnitResponse> buscar(String q) {
        return orgUnitRepository.findByNombreContainingIgnoreCaseOrSiglasContainingIgnoreCaseOrCodigoContainingIgnoreCase(q, q, q)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public OrgUnitResponse obtenerPorId(Long id) {
        OrgUnit unit = orgUnitRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unidad organizacional no encontrada con id: " + id));
        return toResponse(unit);
    }

    public OrgUnitResponse obtenerArbolCompleto() {
        List<OrgUnit> todas = orgUnitRepository.findAll();
        if (todas.isEmpty()) {
            throw new NoSuchElementException("No existen unidades organizacionales");
        }
        OrgUnit raiz = todas.stream().filter(u -> u.getPadre() == null).findFirst()
                .orElse(todas.get(0));
        OrgUnitResponse resp = toResponseConHijos(raiz, todas);
        return resp;
    }

    public List<OrgUnitResponse> listarJerarquia() {
        List<OrgUnit> todas = orgUnitRepository.findByActivoTrueOrderByOrdenAscNombreAsc();
        List<OrgUnit> raices = todas.stream().filter(u -> u.getPadre() == null).collect(Collectors.toList());
        List<OrgUnitResponse> result = new ArrayList<>();
        for (OrgUnit raiz : raices) {
            result.add(toResponseConHijos(raiz, todas));
        }
        return result;
    }

    @Transactional
    public OrgUnitResponse crear(OrgUnitRequest request) {
        if (request.getCodigo() != null && !request.getCodigo().isBlank()) {
            if (orgUnitRepository.existsByCodigo(request.getCodigo())) {
                throw new IllegalArgumentException("Ya existe una unidad con código: " + request.getCodigo());
            }
        }
        OrgUnit unit = new OrgUnit();
        applyRequest(unit, request);
        if (request.getPadreId() != null) {
            OrgUnit padre = orgUnitRepository.findById(request.getPadreId())
                    .orElseThrow(() -> new NoSuchElementException("Unidad padre no encontrada con id: " + request.getPadreId()));
            unit.setPadre(padre);
            unit.setNivel(padre.getNivel() + 1);
        } else {
            unit.setNivel(0);
        }
        OrgUnit saved = orgUnitRepository.save(unit);
        logAudit("CREAR", "OrgUnit", saved.getId(), "Unidad creada: " + saved.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public OrgUnitResponse actualizar(Long id, OrgUnitRequest request) {
        OrgUnit unit = orgUnitRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unidad organizacional no encontrada con id: " + id));
        if (request.getCodigo() != null && !request.getCodigo().isBlank()) {
            if (orgUnitRepository.existsByCodigoAndIdNot(request.getCodigo(), id)) {
                throw new IllegalArgumentException("Ya existe otra unidad con código: " + request.getCodigo());
            }
        }
        String nombreAnterior = unit.getNombre();
        applyRequest(unit, request);
        if (request.getPadreId() != null) {
            if (request.getPadreId().equals(id)) {
                throw new IllegalArgumentException("Una unidad no puede ser padre de sí misma");
            }
            OrgUnit padre = orgUnitRepository.findById(request.getPadreId())
                    .orElseThrow(() -> new NoSuchElementException("Unidad padre no encontrada con id: " + request.getPadreId()));
            unit.setPadre(padre);
            unit.setNivel(padre.getNivel() + 1);
        } else if (request.getPadreId() == null && unit.getPadre() != null) {
            unit.setPadre(null);
            unit.setNivel(0);
        }
        OrgUnit saved = orgUnitRepository.save(unit);
        logAudit("ACTUALIZAR", "OrgUnit", saved.getId(), "Unidad actualizada: " + nombreAnterior + " -> " + saved.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public void toggleActivo(Long id) {
        OrgUnit unit = orgUnitRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unidad organizacional no encontrada con id: " + id));
        unit.setActivo(!Boolean.TRUE.equals(unit.getActivo()));
        orgUnitRepository.save(unit);
        logAudit("TOGGLE", "OrgUnit", id, "Unidad " + (unit.getActivo() ? "activada" : "desactivada") + ": " + unit.getNombre());
    }

    @Transactional
    public void reordenar(Map<String, Integer> ordenMap) {
        for (Map.Entry<String, Integer> entry : ordenMap.entrySet()) {
            Long unitId = Long.valueOf(entry.getKey());
            int nuevoOrden = entry.getValue();
            OrgUnit unit = orgUnitRepository.findById(unitId)
                    .orElseThrow(() -> new NoSuchElementException("Unidad no encontrada: " + unitId));
            unit.setOrden(nuevoOrden);
            orgUnitRepository.save(unit);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        OrgUnit unit = orgUnitRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unidad organizacional no encontrada con id: " + id));
        long hijos = orgUnitRepository.countByPadreId(id);
        if (hijos > 0) {
            throw new IllegalArgumentException("No se puede eliminar la unidad '" + unit.getNombre() + "' porque tiene " + hijos + " sub-unidades dependientes. Elimine primero las unidades hijas.");
        }
        orgUnitRepository.deleteById(id);
        logAudit("ELIMINAR", "OrgUnit", id, "Unidad eliminada: " + unit.getNombre());
    }

    public Map<String, Object> estadisticas() {
        return Map.of(
                "total", orgUnitRepository.count(),
                "activas", orgUnitRepository.countByActivoTrue(),
                "raices", orgUnitRepository.findByPadreIsNullOrderByOrdenAscNombreAsc().size()
        );
    }

    private void applyRequest(OrgUnit unit, OrgUnitRequest request) {
        if (request.getNombre() != null) unit.setNombre(request.getNombre());
        if (request.getSiglas() != null) unit.setSiglas(request.getSiglas());
        if (request.getCodigo() != null) unit.setCodigo(request.getCodigo());
        if (request.getDescripcion() != null) unit.setDescripcion(request.getDescripcion());
        if (request.getTipo() != null) unit.setTipo(request.getTipo());
        if (request.getResponsable() != null) unit.setResponsable(request.getResponsable());
        if (request.getTelefono() != null) unit.setTelefono(request.getTelefono());
        if (request.getCorreo() != null) unit.setCorreo(request.getCorreo());
        if (request.getOrden() != null) unit.setOrden(request.getOrden());
        if (request.getActivo() != null) unit.setActivo(request.getActivo());
    }

    private OrgUnitResponse toResponse(OrgUnit unit) {
        OrgUnitResponse resp = new OrgUnitResponse();
        resp.setId(unit.getId());
        resp.setNombre(unit.getNombre());
        resp.setSiglas(unit.getSiglas());
        resp.setCodigo(unit.getCodigo());
        resp.setDescripcion(unit.getDescripcion());
        resp.setTipo(unit.getTipo());
        resp.setNivel(unit.getNivel());
        resp.setPadreId(unit.getPadre() != null ? unit.getPadre().getId() : null);
        resp.setPadreNombre(unit.getPadre() != null ? unit.getPadre().getNombre() : null);
        resp.setResponsable(unit.getResponsable());
        resp.setTelefono(unit.getTelefono());
        resp.setCorreo(unit.getCorreo());
        resp.setOrden(unit.getOrden());
        resp.setActivo(unit.getActivo());
        resp.setCreatedAt(unit.getCreatedAt());
        resp.setUpdatedAt(unit.getUpdatedAt());
        return resp;
    }

    private OrgUnitResponse toResponseConHijos(OrgUnit unit, List<OrgUnit> todas) {
        OrgUnitResponse resp = toResponse(unit);
        List<OrgUnitResponse> hijos = todas.stream()
                .filter(h -> h.getPadre() != null && h.getPadre().getId().equals(unit.getId()))
                .map(h -> toResponseConHijos(h, todas))
                .collect(Collectors.toList());
        resp.setHijos(hijos);
        return resp;
    }

    private void logAudit(String accion, String entidad, Long entidadId, String detalle) {
        try {
            AuditLog log = new AuditLog();
            log.setUsuario(SecurityContextHolder.getContext().getAuthentication().getName());
            log.setAccion(accion);
            log.setEntidad(entidad);
            log.setEntidadId(entidadId);
            log.setDetalle(detalle);
            log.setFecha(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception ignored) {}
    }
}
