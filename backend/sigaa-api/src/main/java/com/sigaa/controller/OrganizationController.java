package com.sigaa.controller;

import com.sigaa.dto.OrganizationRequest;
import com.sigaa.dto.OrganizationResponse;
import com.sigaa.dto.OrganizationDepartmentRequest;
import com.sigaa.dto.OrganizationDepartmentResponse;
import com.sigaa.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@PreAuthorize("hasRole('administrador')")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public List<OrganizationResponse> listar() {
        return organizationService.listar();
    }

    @GetMapping("/seleccionables")
    @PreAuthorize("hasAnyAuthority('Reuniones:Crear','Reuniones:Editar','Reuniones:Gestionar participantes','Notas:Ver','Notas:Crear','Notas:Editar')")
    public List<Map<String, Object>> seleccionables() {
        return organizationService.seleccionables();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.obtenerPorId(id));
    }

    @GetMapping("/principal")
    public ResponseEntity<OrganizationResponse> obtenerPrincipal() {
        return ResponseEntity.ok(organizationService.obtenerPrincipal());
    }

    @GetMapping("/{id}/departamentos")
    public List<OrganizationDepartmentResponse> listarDepartamentos(@PathVariable Long id) {
        return organizationService.listarDepartamentos(id);
    }

    @PostMapping("/{id}/departamentos")
    public ResponseEntity<OrganizationDepartmentResponse> crearDepartamento(@PathVariable Long id,
            @Valid @RequestBody OrganizationDepartmentRequest request) {
        return ResponseEntity.status(201).body(organizationService.guardarDepartamento(id, null, request));
    }

    @PutMapping("/{id}/departamentos/{departamentoId}")
    public OrganizationDepartmentResponse actualizarDepartamento(@PathVariable Long id, @PathVariable Long departamentoId,
            @Valid @RequestBody OrganizationDepartmentRequest request) {
        return organizationService.guardarDepartamento(id, departamentoId, request);
    }

    @GetMapping("/estado/{estado}")
    public List<OrganizationResponse> listarPorEstado(@PathVariable String estado) {
        return organizationService.listarPorEstado(estado);
    }

    @GetMapping("/tipo/{tipo}")
    public List<OrganizationResponse> listarPorTipo(@PathVariable String tipo) {
        return organizationService.listarPorTipo(tipo);
    }

    @GetMapping("/stats")
    public Map<String, Object> estadisticas() {
        return Map.of("total", organizationService.listar().size(), "activas", organizationService.contarActivas());
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<OrganizationResponse> crear(@Valid @RequestBody OrganizationRequest request) {
        return ResponseEntity.status(201).body(organizationService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<OrganizationResponse> actualizar(@PathVariable Long id, @Valid @RequestBody OrganizationRequest request) {
        return ResponseEntity.ok(organizationService.actualizar(id, request));
    }

    @PatchMapping("/{id}/campo")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<OrganizationResponse> actualizarCampo(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(organizationService.actualizarCampo(id, body.get("campo"), body.get("valor")));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<OrganizationResponse> toggleActivo(@PathVariable Long id) {
        OrganizationResponse org = organizationService.obtenerPorId(id);
        OrganizationRequest req = new OrganizationRequest();
        req.setNombre(org.getNombre());
        req.setActivo(!Boolean.TRUE.equals(org.getActivo()));
        req.setEstado(Boolean.TRUE.equals(org.getActivo()) ? "INACTIVO" : "ACTIVO");
        return ResponseEntity.ok(organizationService.actualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        organizationService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
