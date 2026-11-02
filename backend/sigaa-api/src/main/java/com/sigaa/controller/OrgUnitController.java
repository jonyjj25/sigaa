package com.sigaa.controller;

import com.sigaa.dto.OrgUnitRequest;
import com.sigaa.dto.OrgUnitResponse;
import com.sigaa.service.OrgUnitService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizacion")
@PreAuthorize("hasRole('administrador')")
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    public OrgUnitController(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    @GetMapping
    public List<OrgUnitResponse> listar() {
        return orgUnitService.listar();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrgUnitResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(orgUnitService.obtenerPorId(id));
    }

    @GetMapping("/activos")
    @PreAuthorize("isAuthenticated()")
    public List<OrgUnitResponse> listarActivos() {
        return orgUnitService.listarActivos();
    }

    @GetMapping("/raices")
    public List<OrgUnitResponse> listarRaices() {
        return orgUnitService.listarRaices();
    }

    @GetMapping("/nivel/{nivel}")
    public List<OrgUnitResponse> listarPorNivel(@PathVariable int nivel) {
        return orgUnitService.listarPorNivel(nivel);
    }

    @GetMapping("/tipo/{tipo}")
    public List<OrgUnitResponse> listarPorTipo(@PathVariable String tipo) {
        return orgUnitService.listarPorTipo(tipo);
    }

    @GetMapping("/{id}/hijos")
    public List<OrgUnitResponse> listarHijos(@PathVariable Long id) {
        return orgUnitService.listarHijos(id);
    }

    @GetMapping("/buscar")
    public List<OrgUnitResponse> buscar(@RequestParam String q) {
        return orgUnitService.buscar(q);
    }

    @GetMapping("/arbol")
    public ResponseEntity<OrgUnitResponse> obtenerArbol() {
        return ResponseEntity.ok(orgUnitService.obtenerArbolCompleto());
    }

    @GetMapping("/jerarquia")
    public List<OrgUnitResponse> listarJerarquia() {
        return orgUnitService.listarJerarquia();
    }

    @GetMapping("/stats")
    public Map<String, Object> estadisticas() {
        return orgUnitService.estadisticas();
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<OrgUnitResponse> crear(@Valid @RequestBody OrgUnitRequest request) {
        return ResponseEntity.status(201).body(orgUnitService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<OrgUnitResponse> actualizar(@PathVariable Long id, @Valid @RequestBody OrgUnitRequest request) {
        return ResponseEntity.ok(orgUnitService.actualizar(id, request));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> toggleActivo(@PathVariable Long id) {
        orgUnitService.toggleActivo(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> reordenar(@RequestBody Map<String, Integer> ordenMap) {
        orgUnitService.reordenar(ordenMap);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        orgUnitService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
