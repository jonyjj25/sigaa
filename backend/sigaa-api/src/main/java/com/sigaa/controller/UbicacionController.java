package com.sigaa.controller;

import com.sigaa.model.Ubicacion;
import com.sigaa.repository.UbicacionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ubicaciones")
@PreAuthorize("hasRole('administrador')")
public class UbicacionController {
    private final UbicacionRepository repository;
    public UbicacionController(UbicacionRepository repository) { this.repository = repository; }
    @GetMapping
    public List<Ubicacion> listar() { return repository.findAllByOrderByNombreAsc(); }
    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Ubicacion> crear(@RequestBody Map<String, Object> body) {
        if (!isValid(body)) return ResponseEntity.badRequest().build();
        Ubicacion u = new Ubicacion();
        u.setNombre((String) body.get("nombre"));
        u.setDireccion((String) body.getOrDefault("direccion", ""));
        u.setLatitud(body.containsKey("latitud") ? ((Number) body.get("latitud")).doubleValue() : 0);
        u.setLongitud(body.containsKey("longitud") ? ((Number) body.get("longitud")).doubleValue() : 0);
        u.setRadio(body.containsKey("radio") ? ((Number) body.get("radio")).doubleValue() : 200);
        return ResponseEntity.status(201).body(repository.save(u));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Ubicacion> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!isValidPartial(body)) return ResponseEntity.badRequest().build();
        return repository.findById(id).map(u -> {
            if (body.containsKey("nombre")) u.setNombre((String) body.get("nombre"));
            if (body.containsKey("direccion")) u.setDireccion((String) body.get("direccion"));
            if (body.containsKey("latitud")) u.setLatitud(((Number) body.get("latitud")).doubleValue());
            if (body.containsKey("longitud")) u.setLongitud(((Number) body.get("longitud")).doubleValue());
            if (body.containsKey("radio")) u.setRadio(((Number) body.get("radio")).doubleValue());
            return ResponseEntity.ok(repository.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (repository.existsById(id)) { repository.deleteById(id); return ResponseEntity.noContent().build(); }
        return ResponseEntity.notFound().build();
    }

    private boolean isValid(Map<String, Object> body) {
        Object name = body.get("nombre");
        return name instanceof String value && !value.isBlank() && isValidPartial(body);
    }

    private boolean isValidPartial(Map<String, Object> body) {
        try {
            if (body.containsKey("latitud") && Math.abs(((Number) body.get("latitud")).doubleValue()) > 90) return false;
            if (body.containsKey("longitud") && Math.abs(((Number) body.get("longitud")).doubleValue()) > 180) return false;
            return !body.containsKey("radio") || ((Number) body.get("radio")).doubleValue() > 0;
        } catch (ClassCastException ex) {
            return false;
        }
    }
}
