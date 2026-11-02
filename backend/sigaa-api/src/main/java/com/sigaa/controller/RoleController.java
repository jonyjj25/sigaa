package com.sigaa.controller;

import com.sigaa.dto.PermissionResponse;
import com.sigaa.dto.RoleRequest;
import com.sigaa.dto.RolePermissionsRequest;
import com.sigaa.dto.RoleResponse;
import com.sigaa.dto.UsuarioResponse;
import com.sigaa.model.Role;
import com.sigaa.model.Usuario;
import com.sigaa.repository.RoleRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.RoleAdministrationService;
import com.sigaa.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleRepository roleRepository;
    private final RoleAdministrationService roleAdministrationService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public RoleController(RoleRepository roleRepository, RoleAdministrationService roleAdministrationService,
                          UsuarioRepository usuarioRepository, UsuarioService usuarioService) {
        this.roleRepository = roleRepository;
        this.roleAdministrationService = roleAdministrationService;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @PreAuthorize("hasRole('administrador')")
    public List<RoleResponse> listar() {
        Map<Long, Long> userCounts = buildUserCountMap();
        return roleRepository.findAll().stream()
            .filter(r -> !com.sigaa.service.InstitutionalRoles.retired(r.getNombre()))
            .map(r -> toResponse(r, userCounts))
            .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<RoleResponse> obtener(@PathVariable Long id) {
        return roleRepository.findById(id)
            .map(r -> {
                Map<Long, Long> userCounts = buildUserCountMap();
                return ResponseEntity.ok(toResponse(r, userCounts));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/usuarios")
    @PreAuthorize("hasRole('administrador')")
    public List<UsuarioResponse> obtenerUsuarios(@PathVariable Long id) {
        if (!roleRepository.existsById(id)) {
            throw new NoSuchElementException("Rol no encontrado con id: " + id);
        }

        Set<Usuario> allUsers = new HashSet<>();
        allUsers.addAll(usuarioRepository.findUsuariosByGlobalRolId(id));
        allUsers.addAll(usuarioRepository.findUsuariosByUnidadRolId(id));

        return allUsers.stream()
            .map(u -> usuarioService.obtenerPorId(u.getId()))
            .collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public RoleResponse crear(@Valid @RequestBody RoleRequest request, Authentication auth,
                              HttpServletRequest httpRequest) {
        Role role = roleAdministrationService.create(request, auth.getName(), httpRequest.getRemoteAddr());
        Map<Long, Long> userCounts = buildUserCountMap();
        return toResponse(role, userCounts);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<RoleResponse> actualizar(@PathVariable Long id, @Valid @RequestBody RoleRequest request,
                                                    Authentication auth, HttpServletRequest httpRequest) {
        if (request.getPermisoIds() != null) {
            throw new IllegalArgumentException(
                    "Los permisos deben actualizarse mediante /api/roles/{id}/permissions.");
        }
        Role role = roleAdministrationService.update(id, request, auth.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(toResponse(role, buildUserCountMap()));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<RoleResponse> actualizarPermisos(@PathVariable Long id,
                                                           @Valid @RequestBody RolePermissionsRequest request,
                                                           Authentication auth,
                                                           HttpServletRequest httpRequest) {
        Role role = roleAdministrationService.assignPermissions(
                id, request.getPermisoIds(), auth.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(toResponse(role, buildUserCountMap()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth,
                                         HttpServletRequest httpRequest) {
        roleAdministrationService.archive(id, auth.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{fromRoleId}/copy-permissions/{toRoleId}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> copyPermissions(@PathVariable Long fromRoleId, @PathVariable Long toRoleId,
                                                Authentication auth, HttpServletRequest httpRequest) {
        roleAdministrationService.copyPermissions(
                fromRoleId, toRoleId, auth.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roleId}/reset-permissions")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> resetPermissions(@PathVariable Long roleId, Authentication auth,
                                                 HttpServletRequest httpRequest) {
        roleAdministrationService.resetPermissions(roleId, auth.getName(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    private Map<Long, Long> buildUserCountMap() {
        List<Object[]> rows = usuarioRepository.countUsuariosByRolId();
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            Long rolId = ((Number) row[0]).longValue();
            Long count = ((Number) row[1]).longValue();
            map.put(rolId, count);
        }
        return map;
    }

    private RoleResponse toResponse(Role role, Map<Long, Long> userCounts) {
        List<PermissionResponse> permisoResponses = role.getPermisos().stream()
            .map(p -> new PermissionResponse(p.getId(), p.getModulo(), p.getAccion(), p.getRecurso(), p.getDescripcion(), p.getActivo()))
            .collect(Collectors.toList());
        int userCount = userCounts.getOrDefault(role.getId(), 0L).intValue();
        return new RoleResponse(role.getId(), role.getNombre(), role.getDescripcion(), role.getActivo(),
                role.getSistema(), permisoResponses, userCount);
    }
}
