package com.sigaa.controller;

import com.sigaa.model.NotificationTemplate;
import com.sigaa.repository.NotificationTemplateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificationTemplateController {
    private final NotificationTemplateRepository repository;
    public NotificationTemplateController(NotificationTemplateRepository repository) { this.repository = repository; }

    @GetMapping
    public List<NotificationTemplate> listar() { return repository.findAllByOrderByNombreAsc(); }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationTemplate> obtener(@PathVariable Long id) {
        return repository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationTemplate> crear(@RequestBody Map<String, Object> body) {
        NotificationTemplate nt = new NotificationTemplate();
        if (body.containsKey("nombre")) nt.setNombre((String) body.get("nombre"));
        if (body.containsKey("tipo")) nt.setTipo((String) body.get("tipo"));
        if (body.containsKey("asunto")) nt.setAsunto((String) body.get("asunto"));
        if (body.containsKey("cuerpo")) nt.setCuerpo((String) body.get("cuerpo"));
        if (body.containsKey("activo")) nt.setActivo((Boolean) body.get("activo"));
        return ResponseEntity.ok(repository.save(nt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationTemplate> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return repository.findById(id).map(nt -> {
            if (body.containsKey("nombre")) nt.setNombre((String) body.get("nombre"));
            if (body.containsKey("tipo")) nt.setTipo((String) body.get("tipo"));
            if (body.containsKey("asunto")) nt.setAsunto((String) body.get("asunto"));
            if (body.containsKey("cuerpo")) nt.setCuerpo((String) body.get("cuerpo"));
            if (body.containsKey("activo")) nt.setActivo((Boolean) body.get("activo"));
            return ResponseEntity.ok(repository.save(nt));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
