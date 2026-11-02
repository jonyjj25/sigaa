package com.sigaa.service;

import com.sigaa.model.AuditLog;
import com.sigaa.model.Permission;
import com.sigaa.model.Role;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.PermissionRepository;
import com.sigaa.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;

    public PermissionService(PermissionRepository permissionRepository,
                              RoleRepository roleRepository,
                              AuditLogRepository auditLogRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public List<Permission> findAll() {
        return permissionRepository.findAllByOrderByModuloAscAccionAscRecursoAsc();
    }

    public Optional<Permission> findById(Long id) {
        return permissionRepository.findById(id);
    }

    public List<String> getDistinctModulos() {
        return permissionRepository.findDistinctModulos();
    }

    public List<String> getDistinctAcciones() {
        return permissionRepository.findDistinctAcciones();
    }

    public List<String> getDistinctRecursos() {
        return permissionRepository.findDistinctRecursos();
    }

    @Transactional
    public Permission create(Permission permission, String usuario) {
        if (permissionRepository.existsByModuloAndAccionAndRecurso(
                permission.getModulo(), permission.getAccion(), permission.getRecurso())) {
            throw new IllegalArgumentException(
                "Ya existe un permiso con modulo='" + permission.getModulo() +
                "', accion='" + permission.getAccion() +
                "', recurso='" + permission.getRecurso() + "'");
        }
        Permission saved = permissionRepository.save(permission);
        logAudit(usuario, "CREAR", "Permiso", saved.getId(),
            "Nuevo permiso: " + saved.getModulo() + ":" + saved.getAccion() + ":" + saved.getRecurso());
        return saved;
    }

    @Transactional
    public Permission update(Long id, Permission updated, String usuario) {
        Permission existing = permissionRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Permiso no encontrado con id: " + id));

        Optional<Permission> duplicate = permissionRepository.findDuplicateExcluding(
            updated.getModulo(), updated.getAccion(), updated.getRecurso(), id);
        if (duplicate.isPresent()) {
            throw new IllegalArgumentException(
                "Ya existe otro permiso con modulo='" + updated.getModulo() +
                "', accion='" + updated.getAccion() +
                "', recurso='" + updated.getRecurso() + "'");
        }

        String before = existing.getModulo() + ":" + existing.getAccion() + ":" + existing.getRecurso();
        existing.setModulo(updated.getModulo());
        existing.setAccion(updated.getAccion());
        existing.setRecurso(updated.getRecurso());
        if (updated.getDescripcion() != null) existing.setDescripcion(updated.getDescripcion());
        if (updated.getActivo() != null) existing.setActivo(updated.getActivo());

        Permission saved = permissionRepository.save(existing);
        String after = saved.getModulo() + ":" + saved.getAccion() + ":" + saved.getRecurso();
        logAudit(usuario, "EDITAR", "Permiso", saved.getId(),
            "Permiso actualizado: " + before + " -> " + after);
        return saved;
    }

    @Transactional
    public void delete(Long id, String usuario) {
        Permission permission = permissionRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Permiso no encontrado con id: " + id));

        for (Role role : roleRepository.findAll()) {
            if (role.getPermisos().removeIf(p -> p.getId().equals(id))) {
                roleRepository.save(role);
            }
        }

        permissionRepository.deleteById(id);
        logAudit(usuario, "ELIMINAR", "Permiso", id,
            "Permiso eliminado: " + permission.getModulo() + ":" + permission.getAccion() + ":" + permission.getRecurso());
    }

    @Transactional
    public Permission toggle(Long id, String usuario) {
        Permission permission = permissionRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Permiso no encontrado con id: " + id));
        permission.setActivo(!Boolean.TRUE.equals(permission.getActivo()));
        Permission saved = permissionRepository.save(permission);
        logAudit(usuario, "CAMBIAR_ESTADO", "Permiso", saved.getId(),
            "Permiso " + (saved.getActivo() ? "activado" : "desactivado") + ": " +
            saved.getModulo() + ":" + saved.getAccion() + ":" + saved.getRecurso());
        return saved;
    }

    public Map<String, Object> getStats() {
        long total = permissionRepository.count();
        long activos = permissionRepository.countByActivoTrue();
        long modulos = permissionRepository.findDistinctModulos().size();
        long acciones = permissionRepository.findDistinctAcciones().size();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("activos", activos);
        stats.put("inactivos", total - activos);
        stats.put("modulos", modulos);
        stats.put("acciones", acciones);
        return stats;
    }

    public List<Map<String, Object>> getMatrix() {
        List<Permission> permissions = findAll();
        List<Role> roles = roleRepository.findAll();

        Map<Long, Set<Long>> rolePermMap = new HashMap<>();
        for (Role role : roles) {
            rolePermMap.put(role.getId(),
                role.getPermisos().stream().map(Permission::getId).collect(Collectors.toSet()));
        }

        List<Map<String, Object>> matrix = new ArrayList<>();
        for (Permission perm : permissions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", perm.getId());
            row.put("modulo", perm.getModulo());
            row.put("accion", perm.getAccion());
            row.put("recurso", perm.getRecurso());
            row.put("descripcion", perm.getDescripcion());
            row.put("activo", perm.getActivo());

            Map<Long, Boolean> roleAssignments = new LinkedHashMap<>();
            for (Role role : roles) {
                roleAssignments.put(role.getId(),
                    rolePermMap.getOrDefault(role.getId(), Set.of()).contains(perm.getId()));
            }
            row.put("roles", roleAssignments);
            matrix.add(row);
        }
        return matrix;
    }

    private void logAudit(String usuario, String accion, String entidad, Long entidadId, String detalle) {
        AuditLog log = new AuditLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setDetalle(detalle);
        auditLogRepository.save(log);
    }
}
