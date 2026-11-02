package com.sigaa.controller;

import com.sigaa.service.AdminPasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/password-reset-requests")
@PreAuthorize("hasRole('administrador')")
public class AdminPasswordResetController {
    private final AdminPasswordResetService service;
    public AdminPasswordResetController(AdminPasswordResetService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> pending() { return ResponseEntity.ok(service.pending()); }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        try { return ResponseEntity.ok(service.approve(id, auth.getName(), request.getRemoteAddr())); }
        catch (IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())); }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                                    Authentication auth, HttpServletRequest request) {
        try {
            service.reject(id, auth.getName(), body == null ? null : body.get("reason"), request.getRemoteAddr());
            return ResponseEntity.ok(Map.of("message", "Solicitud rechazada"));
        } catch (IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())); }
    }
}
