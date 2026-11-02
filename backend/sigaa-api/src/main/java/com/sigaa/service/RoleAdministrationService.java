package com.sigaa.service;

import com.sigaa.dto.RoleRequest;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Permission;
import com.sigaa.model.Role;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.PermissionRepository;
import com.sigaa.repository.RoleRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAdministrationService {

    private static final String SUPERADMIN_ROLE = "Superadmin";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogRepository auditLogRepository;

    public RoleAdministrationService(RoleRepository roleRepository,
                                     PermissionRepository permissionRepository,
                                     AuditLogRepository auditLogRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public Role create(RoleRequest request, String actor, String ip) {
        String name = normalizeName(request.getNombre());
        requireAssignableName(name);
        ensureUniqueName(name, null);

        Role role = new Role();
        role.setNombre(name);
        role.setDescripcion(normalizeDescription(request.getDescripcion()));
        role.setActivo(request.getActivo() == null || Boolean.TRUE.equals(request.getActivo()));
        role.setSistema(false);
        role.setPermisos(resolvePermissions(request.getPermisoIds()));

        Role saved = roleRepository.save(role);
        audit(actor, ip, "CREAR_ROL", saved,
                "Rol creado: nombre='" + saved.getNombre() + "', activo=" + saved.getActivo()
                        + ", permisos=" + permissionIds(saved.getPermisos()));
        return saved;
    }

    @Transactional
    public Role update(Long id, RoleRequest request, String actor, String ip) {
        Role role = findRole(id);
        requireAssignableName(role.getNombre());
        String name = normalizeName(request.getNombre());
        requireAssignableName(name);
        String description = normalizeDescription(request.getDescripcion());
        boolean requestedActive = request.getActivo() == null
                ? Boolean.TRUE.equals(role.getActivo())
                : Boolean.TRUE.equals(request.getActivo());

        if (Boolean.TRUE.equals(role.getSistema())) {
            if (!role.getNombre().equals(name)) {
                throw new IllegalStateException("El nombre de un rol institucional no puede modificarse.");
            }
            if (!requestedActive) {
                throw new IllegalStateException("Un rol institucional no puede desactivarse.");
            }
        }

        ensureUniqueName(name, id);
        String before = "nombre='" + role.getNombre() + "', descripcion='" + safe(role.getDescripcion())
                + "', activo=" + role.getActivo();
        role.setNombre(name);
        role.setDescripcion(description);
        role.setActivo(requestedActive);
        Role saved = roleRepository.save(role);
        audit(actor, ip, "ACTUALIZAR_ROL", saved,
                "Antes: {" + before + "}; Después: {nombre='" + saved.getNombre()
                        + "', descripcion='" + safe(saved.getDescripcion()) + "', activo=" + saved.getActivo() + "}");
        return saved;
    }

    @Transactional
    public Role assignPermissions(Long id, Set<Long> permissionIds, String actor, String ip) {
        Role role = findRole(id);
        requireAssignableName(role.getNombre());
        Set<Permission> permissions = resolvePermissions(permissionIds);
        validateSystemPermissions(role, permissions);

        List<Long> before = permissionIds(role.getPermisos());
        List<Long> after = permissionIds(permissions);
        if (before.equals(after)) {
            return role;
        }

        role.setPermisos(permissions);
        Role saved = roleRepository.save(role);
        audit(actor, ip, "ACTUALIZAR_PERMISOS_ROL", saved,
                "Permisos anteriores=" + before + "; permisos nuevos=" + after);
        return saved;
    }

    private void requireAssignableName(String name) {
        if (InstitutionalRoles.retired(name)) {
            throw new IllegalStateException("El rol Administrador está retirado y no puede utilizarse.");
        }
    }

    @Transactional
    public Role archive(Long id, String actor, String ip) {
        Role role = findRole(id);
        if (Boolean.TRUE.equals(role.getSistema())) {
            throw new IllegalStateException("Los roles institucionales no pueden archivarse.");
        }
        if (!Boolean.TRUE.equals(role.getActivo())) {
            return role;
        }
        role.setActivo(false);
        Role saved = roleRepository.save(role);
        audit(actor, ip, "ARCHIVAR_ROL", saved,
                "Rol archivado sin eliminar sus asignaciones ni su historial.");
        return saved;
    }

    @Transactional
    public Role copyPermissions(Long fromRoleId, Long toRoleId, String actor, String ip) {
        Role source = findRole(fromRoleId);
        return assignPermissions(toRoleId,
                source.getPermisos().stream().map(Permission::getId).collect(Collectors.toSet()), actor, ip);
    }

    @Transactional
    public Role resetPermissions(Long roleId, String actor, String ip) {
        Role role = findRole(roleId);
        if (Boolean.TRUE.equals(role.getSistema())) {
            throw new IllegalStateException("Los permisos de un rol institucional no pueden restablecerse a vacío.");
        }
        return assignPermissions(roleId, Set.of(), actor, ip);
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Rol no encontrado con id: " + id));
    }

    private Set<Permission> resolvePermissions(Set<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Permission> permissions = permissionRepository.findAllById(requestedIds);
        if (permissions.size() != requestedIds.size()) {
            Set<Long> found = permissions.stream().map(Permission::getId).collect(Collectors.toSet());
            List<Long> missing = requestedIds.stream().filter(id -> !found.contains(id)).sorted().toList();
            throw new IllegalArgumentException("Permisos inexistentes: " + missing);
        }
        List<Long> inactive = permissions.stream()
                .filter(permission -> !Boolean.TRUE.equals(permission.getActivo()))
                .map(Permission::getId)
                .sorted()
                .toList();
        if (!inactive.isEmpty()) {
            throw new IllegalArgumentException("No se pueden asignar permisos inactivos: " + inactive);
        }
        return new HashSet<>(permissions);
    }

    private void validateSystemPermissions(Role role, Set<Permission> permissions) {
        if (!Boolean.TRUE.equals(role.getSistema())) {
            return;
        }
        if (permissions.isEmpty()) {
            throw new IllegalStateException("Un rol institucional debe conservar al menos un permiso.");
        }
        if (SUPERADMIN_ROLE.equalsIgnoreCase(role.getNombre().trim())) {
            boolean keepsRoleManagement = permissions.stream().anyMatch(permission ->
                    "Configuración".equalsIgnoreCase(permission.getModulo())
                            && "Gestionar roles".equalsIgnoreCase(permission.getAccion()));
            if (!keepsRoleManagement) {
                throw new IllegalStateException("El Superadmin debe conservar el permiso para gestionar roles.");
            }
        }
    }

    private void ensureUniqueName(String name, Long currentId) {
        boolean duplicate = currentId == null
                ? roleRepository.existsByNombreIgnoreCase(name)
                : roleRepository.existsByNombreIgnoreCaseAndIdNot(name, currentId);
        if (duplicate) {
            throw new IllegalArgumentException("Ya existe un rol con ese nombre.");
        }
    }

    private String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("El nombre del rol es obligatorio.");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("El nombre del rol es obligatorio.");
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        return value == null ? "" : value.trim();
    }

    private List<Long> permissionIds(Set<Permission> permissions) {
        if (permissions == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(permissions.stream().map(Permission::getId).toList());
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void audit(String actor, String ip, String action, Role role, String detail) {
        AuditLog log = new AuditLog();
        log.setUsuario(actor == null || actor.isBlank() ? "sistema" : actor);
        log.setAccion(action);
        log.setEntidad("Rol");
        log.setEntidadId(role.getId());
        log.setDireccionIp(ip);
        log.setDetalle(detail);
        auditLogRepository.save(log);
    }
}
