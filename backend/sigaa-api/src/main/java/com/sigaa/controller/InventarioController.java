package com.sigaa.controller;

import com.sigaa.dto.InventarioCreateRequest;
import com.sigaa.dto.InventarioUpdateRequest;
import com.sigaa.dto.InventarioResponse;
import com.sigaa.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventario")
public class InventarioController {
    private final InventarioService service;

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('Inventario:Ver')")
    public List<InventarioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/asignables")
    @PreAuthorize("hasAnyAuthority('Inventario:Crear', 'Inventario:Editar')")
    public Map<String, List<InventarioResponse.OpcionAsignacion>> asignables() {
        return service.asignables();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Inventario:Crear')")
    public ResponseEntity<InventarioResponse> crear(@Valid @RequestBody InventarioCreateRequest body) {
        return ResponseEntity.status(201).body(service.crear(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('Inventario:Editar')")
    public InventarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody InventarioUpdateRequest body) {
        return service.actualizar(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('Inventario:Eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> datosInvalidos() {
        return ResponseEntity.badRequest().body(Map.of("error", "Datos inválidos",
                "message", "Revisa el formato de la fecha (AAAA-MM-DD) y los campos enviados."));
    }
}
