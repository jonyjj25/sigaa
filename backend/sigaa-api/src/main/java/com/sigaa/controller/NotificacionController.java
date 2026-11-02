package com.sigaa.controller;

import com.sigaa.model.Notificacion;
import com.sigaa.repository.NotificacionRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.InternalNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final InternalNotificationService notificationService;

    public NotificacionController(NotificacionRepository notificacionRepository,
                                  UsuarioRepository usuarioRepository, InternalNotificationService notificationService) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/mias")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> misNotificaciones(Authentication auth) {
        Long usuarioId = usuarioRepository.findIdByCorreo(auth.getName()).orElse(null);
        if (usuarioId == null) return List.of();
        return notificationService.recientes(usuarioId)
            .stream().map(this::toMap).collect(Collectors.toList());
    }

    @PutMapping("/leer-todas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarTodasComoLeidas(Authentication auth) {
        Long usuarioId = usuarioRepository.findIdByCorreo(auth.getName()).orElse(null);
        if (usuarioId == null) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        notificationService.marcarTodasComoLeidas(usuarioId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PutMapping("/{id}/leer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarComoLeida(@PathVariable Long id, Authentication auth) {
        var opt = notificacionRepository.findById(id);
        if (opt.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        Notificacion n = opt.get();
        Long usuarioId = usuarioRepository.findIdByCorreo(auth.getName()).orElse(null);
        if (usuarioId == null || !n.getUsuario().getId().equals(usuarioId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        notificationService.marcarComoLeida(n);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/no-leidas")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> noLeidas(Authentication auth) {
        Long usuarioId = usuarioRepository.findIdByCorreo(auth.getName()).orElse(null);
        long count = (usuarioId == null) ? 0 : notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
        Map<String, Object> m = new HashMap<>();
        m.put("count", count);
        return m;
    }

    private Map<String, Object> toMap(Notificacion n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(n.getId()));
        m.put("title", n.getTitulo());
        m.put("message", n.getMensaje());
        m.put("type", "info");
        m.put("timestamp", n.getCreatedAt().toString());
        m.put("read", n.getLeida());
        return m;
    }
}
