package com.sigaa.controller;

import com.sigaa.dto.ModuloRequest;
import com.sigaa.dto.ModuloResponse;
import com.sigaa.dto.ModuloEstadoRequest;
import com.sigaa.dto.ModuloOrdenRequest;
import com.sigaa.dto.ModuloConfiguracionRequest;
import com.sigaa.service.ModuloService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modulos")
@PreAuthorize("hasRole('administrador')")
public class ModuloController {

    private final ModuloService moduloService;

    public ModuloController(ModuloService moduloService) {
        this.moduloService = moduloService;
    }

    @GetMapping
    public List<ModuloResponse> listar() {
        return moduloService.listarTodos();
    }

    @GetMapping("/activos")
    public List<ModuloResponse> listarActivos() {
        return moduloService.listarActivos();
    }

    @GetMapping("/menu")
    @PreAuthorize("isAuthenticated()")
    public List<ModuloResponse> listarMenu() {
        return moduloService.listarMenu();
    }

    @GetMapping("/dashboard")
    public List<ModuloResponse> listarDashboard() {
        return moduloService.listarDashboard();
    }

    @GetMapping("/categoria/{categoria}")
    public List<ModuloResponse> listarPorCategoria(@PathVariable String categoria) {
        return moduloService.listarPorCategoria(categoria);
    }

    @GetMapping("/buscar")
    public List<ModuloResponse> buscar(@RequestParam String q) {
        return moduloService.buscar(q);
    }

    @GetMapping("/stats")
    public Map<String, Object> estadisticas() {
        return moduloService.getEstadisticas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModuloResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(moduloService.obtenerPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<ModuloResponse> obtenerPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(moduloService.obtenerPorCodigo(codigo));
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ModuloResponse> crear(@Valid @RequestBody ModuloRequest request) {
        ModuloResponse resp = moduloService.crear(request);
        return ResponseEntity.status(201).body(resp);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ModuloResponse> actualizar(@PathVariable Long id, @RequestBody ModuloRequest request) {
        return ResponseEntity.ok(moduloService.actualizar(id, request));
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasRole('administrador')")
    public List<ModuloResponse> reordenar(@Valid @RequestBody List<@Valid ModuloOrdenRequest> items) {
        return moduloService.reordenar(items);
    }

    @PutMapping("/configuracion")
    @PreAuthorize("hasRole('administrador')")
    public List<ModuloResponse> guardarConfiguracion(
            @Valid @RequestBody List<@Valid ModuloConfiguracionRequest> items) {
        return moduloService.guardarConfiguracion(items);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ModuloResponse> establecerEstado(
            @PathVariable Long id,
            @Valid @RequestBody ModuloEstadoRequest request) {
        return ResponseEntity.ok(moduloService.establecerEstado(id, request.getActivo()));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ModuloResponse> toggleActivo(@PathVariable Long id) {
        return ResponseEntity.ok(moduloService.toggleActivo(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        moduloService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
