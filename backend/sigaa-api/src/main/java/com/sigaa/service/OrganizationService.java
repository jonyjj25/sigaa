package com.sigaa.service;

import com.sigaa.dto.OrganizationRequest;
import com.sigaa.dto.OrganizationResponse;
import com.sigaa.dto.OrganizationDepartmentRequest;
import com.sigaa.dto.OrganizationDepartmentResponse;
import com.sigaa.model.OrganizationDepartment;
import com.sigaa.repository.OrganizationDepartmentRepository;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Organization;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final AuditLogRepository auditLogRepository;
    private final OrganizationDepartmentRepository departmentRepository;

    public OrganizationService(OrganizationRepository organizationRepository, AuditLogRepository auditLogRepository,
                               OrganizationDepartmentRepository departmentRepository) {
        this.organizationRepository = organizationRepository;
        this.auditLogRepository = auditLogRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> seleccionables() {
        return organizationRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(o -> java.util.Map.<String, Object>of("id", o.getId(), "nombre", o.getNombre(),
                        "siglas", o.getSiglas() == null ? "" : o.getSiglas())).toList();
    }

    public List<OrganizationResponse> listar() {
        return organizationRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public OrganizationResponse obtenerPorId(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Organización no encontrada con id: " + id));
        return toResponse(org);
    }

    public OrganizationResponse obtenerPrincipal() {
        Organization org = organizationRepository.findFirstByActivoTrueOrderByIdAsc()
                .orElseGet(() -> organizationRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new NoSuchElementException("No existe organización principal")));
        return toResponse(org);
    }

    public List<OrganizationResponse> listarPorEstado(String estado) {
        return organizationRepository.findByEstadoOrderByNombreAsc(estado).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<OrganizationResponse> listarPorTipo(String tipo) {
        return organizationRepository.findByTipoOrganizacionOrderByNombreAsc(tipo).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public long contarActivas() {
        return organizationRepository.countByActivoTrue();
    }

    @Transactional
    public OrganizationResponse crear(OrganizationRequest request) {
        if (request.getCodigoInstitucional() != null && !request.getCodigoInstitucional().isBlank()) {
            if (organizationRepository.existsByCodigoInstitucional(request.getCodigoInstitucional())) {
                throw new IllegalArgumentException("Ya existe una organización con código institucional: " + request.getCodigoInstitucional());
            }
        }
        if (request.getRuc() != null && !request.getRuc().isBlank()) {
            if (organizationRepository.existsByRuc(request.getRuc())) {
                throw new IllegalArgumentException("Ya existe una organización con RUC: " + request.getRuc());
            }
        }
        Organization org = new Organization();
        applyRequest(org, request);
        Organization saved = organizationRepository.save(org);
        logAudit("CREAR", "Organization", saved.getId(), "Organización creada: " + saved.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public OrganizationResponse actualizar(Long id, OrganizationRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Organización no encontrada con id: " + id));
        if (request.getCodigoInstitucional() != null && !request.getCodigoInstitucional().isBlank()) {
            if (organizationRepository.existsByCodigoInstitucionalAndIdNot(request.getCodigoInstitucional(), id)) {
                throw new IllegalArgumentException("Ya existe otra organización con código: " + request.getCodigoInstitucional());
            }
        }
        if (request.getRuc() != null && !request.getRuc().isBlank()) {
            if (organizationRepository.existsByRucAndIdNot(request.getRuc(), id)) {
                throw new IllegalArgumentException("Ya existe otra organización con RUC: " + request.getRuc());
            }
        }
        String nombreAnterior = org.getNombre();
        String directorAnterior = org.getDirector();
        String cargoAnterior = org.getDirectorCargo();
        applyRequest(org, request);
        Organization saved = organizationRepository.save(org);
        logAudit("ACTUALIZAR", "Organization", saved.getId(), "Organización actualizada: " + nombreAnterior + " -> " + saved.getNombre());
        if (!java.util.Objects.equals(directorAnterior, saved.getDirector())
                || !java.util.Objects.equals(cargoAnterior, saved.getDirectorCargo())) {
            logAudit("CAMBIAR_DIRECTOR", "Organization", id,
                "Director: " + directorAnterior + " (" + cargoAnterior + ") -> "
                    + saved.getDirector() + " (" + saved.getDirectorCargo() + ")");
        }
        return toResponse(saved);
    }

    @Transactional
    public OrganizationResponse actualizarCampo(Long id, String campo, String valor) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Organización no encontrada con id: " + id));
        switch (campo) {
            case "nombre" -> org.setNombre(valor);
            case "siglas" -> org.setSiglas(valor);
            default -> throw new IllegalArgumentException("Campo no actualizable: " + campo);
        }
        Organization saved = organizationRepository.save(org);
        logAudit("ACTUALIZAR_CAMPO", "Organization", saved.getId(), "Campo " + campo + " actualizado");
        return toResponse(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Organización no encontrada con id: " + id));
        org.setActivo(false);
        org.setEstado("INACTIVO");
        organizationRepository.save(org);
        logAudit("ELIMINAR", "Organization", id, "Organización desactivada: " + org.getNombre());
    }

    @Transactional
    public void eliminarDefinitivo(Long id) {
        if (!organizationRepository.existsById(id)) {
            throw new NoSuchElementException("Organización no encontrada con id: " + id);
        }
        organizationRepository.deleteById(id);
        logAudit("ELIMINAR_DEFINITIVO", "Organization", id, "Organización eliminada permanentemente");
    }

    @Transactional(readOnly = true)
    public List<OrganizationDepartmentResponse> listarDepartamentos(Long organizationId) {
        obtenerInstitucion(organizationId);
        return departmentRepository.findByOrganizationIdOrderByNombreAsc(organizationId).stream()
            .map(this::departmentResponse).toList();
    }

    @Transactional
    public OrganizationDepartmentResponse guardarDepartamento(Long organizationId, Long departmentId,
                                                               OrganizationDepartmentRequest request) {
        Organization org = obtenerInstitucion(organizationId);
        String nombre = request.nombre() == null ? "" : request.nombre().trim();
        if (nombre.isBlank()) throw new IllegalArgumentException("El nombre del departamento es obligatorio.");
        boolean duplicado = departmentId == null
            ? departmentRepository.existsByOrganizationIdAndNombreIgnoreCase(organizationId, nombre)
            : departmentRepository.existsByOrganizationIdAndNombreIgnoreCaseAndIdNot(organizationId, nombre, departmentId);
        if (duplicado) throw new IllegalArgumentException("Ya existe un departamento con ese nombre en la institución.");
        OrganizationDepartment department = departmentId == null ? new OrganizationDepartment()
            : departmentRepository.findByIdAndOrganizationId(departmentId, organizationId)
                .orElseThrow(() -> new NoSuchElementException("El departamento no pertenece a esta institución."));
        String anterior = department.getNombre() + ": " + department.getDirector() + " (" + department.getDirectorCargo() + ")";
        department.setOrganization(org);
        department.setNombre(nombre);
        department.setDirector(request.director() == null ? "" : request.director().trim());
        department.setDirectorCargo(request.directorCargo() == null ? "" : request.directorCargo().trim());
        department.setActivo(request.activo());
        OrganizationDepartment saved = departmentRepository.saveAndFlush(department);
        logAudit(departmentId == null ? "CREAR_DEPARTAMENTO" : "ACTUALIZAR_DEPARTAMENTO", "OrganizationDepartment", saved.getId(),
            "Institución " + org.getNombre() + "; anterior: " + anterior + "; actual: " + saved.getNombre()
                + ": " + saved.getDirector() + " (" + saved.getDirectorCargo() + "); activo: " + saved.isActivo());
        return departmentResponse(saved);
    }

    private Organization obtenerInstitucion(Long id) {
        return organizationRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("La institución no existe."));
    }

    private OrganizationDepartmentResponse departmentResponse(OrganizationDepartment department) {
        return new OrganizationDepartmentResponse(department.getId(), department.getNombre(), department.getDirector(),
            department.getDirectorCargo(), department.isActivo());
    }

    private void applyRequest(Organization org, OrganizationRequest request) {
        if (request.getNombre() != null) org.setNombre(request.getNombre().trim());
        if (request.getSiglas() != null) org.setSiglas(request.getSiglas());
        if (request.getCodigoInstitucional() != null) org.setCodigoInstitucional(request.getCodigoInstitucional());
        if (request.getTipoOrganizacion() != null) org.setTipoOrganizacion(request.getTipoOrganizacion());
        if (request.getRuc() != null) org.setRuc(request.getRuc());
        if (request.getDirector() != null) org.setDirector(request.getDirector().trim());
        if (request.getDirectorCargo() != null) org.setDirectorCargo(request.getDirectorCargo().trim());
        if (request.getEstado() != null) org.setEstado(request.getEstado());
        if (request.getActivo() != null) org.setActivo(request.getActivo());
    }

    private OrganizationResponse toResponse(Organization org) {
        OrganizationResponse resp = new OrganizationResponse();
        resp.setId(org.getId());
        resp.setNombre(org.getNombre());
        resp.setSiglas(org.getSiglas());
        resp.setCodigoInstitucional(org.getCodigoInstitucional());
        resp.setTipoOrganizacion(org.getTipoOrganizacion());
        resp.setRuc(org.getRuc());
        resp.setDirector(org.getDirector());
        resp.setDirectorCargo(org.getDirectorCargo());
        resp.setEstado(org.getEstado());
        resp.setActivo(org.getActivo());
        resp.setCreatedAt(org.getCreatedAt());
        resp.setUpdatedAt(org.getUpdatedAt());
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
