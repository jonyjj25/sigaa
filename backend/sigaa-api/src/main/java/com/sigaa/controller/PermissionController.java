package com.sigaa.controller;

import com.sigaa.dto.PermissionResponse;
import com.sigaa.model.Permission;
import com.sigaa.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permisos")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('administrador')")
    public List<PermissionResponse> listar() {
        return permissionService.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<PermissionResponse> obtener(@PathVariable Long id) {
        return permissionService.findById(id)
            .map(p -> ResponseEntity.ok(toResponse(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<PermissionResponse> crear(@RequestBody PermissionRequest request, Authentication auth) {
        return ResponseEntity.status(405).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<PermissionResponse> actualizar(@PathVariable Long id, @RequestBody PermissionRequest request, Authentication auth) {
        return ResponseEntity.status(405).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.status(405).build();
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<PermissionResponse> toggle(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.status(405).build();
    }

    @GetMapping("/modules")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<String>> getModules() {
        return ResponseEntity.ok(permissionService.getDistinctModulos());
    }

    @GetMapping("/actions")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<String>> getActions() {
        return ResponseEntity.ok(permissionService.getDistinctAcciones());
    }

    @GetMapping("/resources")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<String>> getResources() {
        return ResponseEntity.ok(permissionService.getDistinctRecursos());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(permissionService.getStats());
    }

    @GetMapping("/matrix")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<Map<String, Object>>> getMatrix() {
        return ResponseEntity.ok(permissionService.getMatrix());
    }

    private PermissionResponse toResponse(Permission p) {
        return new PermissionResponse(p.getId(), p.getModulo(), p.getAccion(),
            p.getRecurso(), p.getDescripcion(), p.getActivo());
    }

    public static class PermissionRequest {
        private String modulo;
        private String accion;
        private String recurso;
        private String descripcion;
        private Boolean activo;

        public String getModulo() { return modulo; }
        public void setModulo(String modulo) { this.modulo = modulo; }
        public String getAccion() { return accion; }
        public void setAccion(String accion) { this.accion = accion; }
        public String getRecurso() { return recurso; }
        public void setRecurso(String recurso) { this.recurso = recurso; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public Boolean getActivo() { return activo; }
        public void setActivo(Boolean activo) { this.activo = activo; }
    }
}
