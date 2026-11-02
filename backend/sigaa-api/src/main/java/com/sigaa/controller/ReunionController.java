package com.sigaa.controller;

import com.sigaa.dto.ReunionRequest;
import com.sigaa.dto.ReunionResponse;
import com.sigaa.dto.EstadoRequest;
import com.sigaa.service.ReunionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reuniones")
public class ReunionController {

    private final ReunionService service;

    public ReunionController(ReunionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('Reuniones:Ver')")
    public List<ReunionResponse> listar() {
        return service.listarTodas();
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAuthority('Reuniones:Ver')")
    public List<ReunionResponse> buscar(@RequestParam String q) {
        return service.buscar(q);
    }

    @GetMapping("/estado/{estado}")
    @PreAuthorize("hasAuthority('Reuniones:Ver')")
    public List<ReunionResponse> listarPorEstado(@PathVariable String estado) {
        return service.listarPorEstado(estado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Reuniones:Ver')")
    public ResponseEntity<ReunionResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    @PreAuthorize("hasAuthority('Reuniones:Ver')")
    public ResponseEntity<ReunionResponse> obtenerPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(service.obtenerPorCodigo(codigo));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Reuniones:Crear')")
    public ResponseEntity<ReunionResponse> crear(@Valid @RequestBody ReunionRequest request) {
        return ResponseEntity.status(201).body(service.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Reuniones:Editar','Reuniones:Gestionar participantes')")
    public ResponseEntity<ReunionResponse> actualizar(@PathVariable Long id, @Valid @RequestBody ReunionRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyAuthority('Reuniones:Iniciar','Reuniones:Cancelar')")
    public ResponseEntity<ReunionResponse> cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest body) {
        return ResponseEntity.ok(service.cambiarEstado(id, body.getEstado()));
    }

    @PostMapping("/{id}/duplicar")
    @PreAuthorize("hasAuthority('Reuniones:Duplicar')")
    public ResponseEntity<ReunionResponse> duplicar(@PathVariable Long id) {
        return ResponseEntity.status(201).body(service.duplicar(id));
    }

    @PutMapping("/{id}/reprogramar")
    @PreAuthorize("hasRole('superadmin') or (hasAnyRole('Director','Directora','Subdirector','Subdirectora') and hasAuthority('Reuniones:Editar'))")
    public ResponseEntity<ReunionResponse> reprogramar(@PathVariable Long id, @Valid @RequestBody ReunionRequest request) {
        return ResponseEntity.ok(service.reprogramar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('Reuniones:Eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
