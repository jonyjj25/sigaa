package com.sigaa.controller;

import com.sigaa.dto.*;
import com.sigaa.service.NotificationEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones/eventos")
@PreAuthorize("hasRole('administrador')")
public class NotificationEventController {

    private final NotificationEventService service;

    public NotificationEventController(NotificationEventService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationEventResponse> listar() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationEventResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationEventResponse> crear(@Valid @RequestBody NotificationEventRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationEventResponse> actualizar(@PathVariable Long id,
                                                                 @Valid @RequestBody NotificationEventRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationEventResponse> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggleActivo(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/settings")
    public ResponseEntity<List<NotificationSettingResponse>> listarSettings(@PathVariable Long id) {
        return ResponseEntity.ok(service.findSettingsByEvento(id));
    }

    @PostMapping("/{id}/settings")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationSettingResponse> crearSetting(@PathVariable Long id,
                                                                     @Valid @RequestBody NotificationSettingRequest request) {
        request.setEventoId(id);
        return ResponseEntity.ok(service.createSetting(id, request));
    }

    @PutMapping("/settings/{settingId}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationSettingResponse> actualizarSetting(@PathVariable Long settingId,
                                                                          @Valid @RequestBody NotificationSettingRequest request) {
        return ResponseEntity.ok(service.updateSetting(settingId, request));
    }

    @DeleteMapping("/settings/{settingId}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminarSetting(@PathVariable Long settingId) {
        service.deleteSetting(settingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<List<NotificationRecipientResponse>> listarRecipients(@PathVariable Long id) {
        return ResponseEntity.ok(service.findRecipientsByEvento(id));
    }

    @PostMapping("/{id}/recipients")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationRecipientResponse> crearRecipient(@PathVariable Long id,
                                                                         @Valid @RequestBody NotificationRecipientRequest request) {
        request.setEventoId(id);
        return ResponseEntity.ok(service.createRecipient(id, request));
    }

    @PatchMapping("/recipients/{recipientId}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<NotificationRecipientResponse> toggleRecipient(@PathVariable Long recipientId) {
        return ResponseEntity.ok(service.toggleRecipient(recipientId));
    }

    @DeleteMapping("/recipients/{recipientId}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminarRecipient(@PathVariable Long recipientId) {
        service.deleteRecipient(recipientId);
        return ResponseEntity.noContent().build();
    }
}
