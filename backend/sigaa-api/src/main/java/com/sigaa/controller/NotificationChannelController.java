package com.sigaa.controller;

import com.sigaa.dto.NotificationChannelRequest;
import com.sigaa.dto.NotificationChannelResponse;
import com.sigaa.service.NotificationChannelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones/canales")
@PreAuthorize("hasRole('administrador')")
public class NotificationChannelController {

    private final NotificationChannelService service;

    public NotificationChannelController(NotificationChannelService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationChannelResponse> listar() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationChannelResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationChannelResponse> crear(@Valid @RequestBody NotificationChannelRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationChannelResponse> actualizar(@PathVariable Long id,
                                                                   @Valid @RequestBody NotificationChannelRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationChannelResponse> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggleActivo(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
