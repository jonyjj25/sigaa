package com.sigaa.controller;

import com.sigaa.model.Unidad;
import com.sigaa.repository.UnidadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unidades")
public class UnidadController {

    private final UnidadRepository unidadRepository;

    public UnidadController(UnidadRepository unidadRepository) {
        this.unidadRepository = unidadRepository;
    }

    @GetMapping
    public List<Unidad> listar() {
        return unidadRepository.findByActivoTrue();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Unidad> obtener(@PathVariable Long id) {
        return unidadRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> crear(@RequestBody Map<String, String> body) {
        if (body.get("nombre") == null || body.get("nombre").isBlank()) {
            return ResponseEntity.badRequest().body("El nombre es obligatorio");
        }
        if (unidadRepository.findByNombre(body.get("nombre")).isPresent()) {
            return ResponseEntity.badRequest().body("La unidad ya existe");
        }
        Unidad unidad = new Unidad();
        unidad.setNombre(body.get("nombre"));
        unidad.setDescripcion(body.getOrDefault("descripcion", ""));
        unidad.setActivo(true);
        return ResponseEntity.ok(unidadRepository.save(unidad));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return unidadRepository.findById(id)
            .map(unidad -> {
                if (body.get("nombre") != null && !body.get("nombre").isBlank()) {
                    unidad.setNombre(body.get("nombre"));
                }
                if (body.containsKey("descripcion")) {
                    unidad.setDescripcion(body.get("descripcion"));
                }
                if (body.containsKey("activo")) {
                    unidad.setActivo(Boolean.parseBoolean(body.get("activo")));
                }
                return ResponseEntity.ok(unidadRepository.save(unidad));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (unidadRepository.existsById(id)) {
            unidadRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
