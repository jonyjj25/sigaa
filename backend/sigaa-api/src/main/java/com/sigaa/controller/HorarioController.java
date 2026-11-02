package com.sigaa.controller;

import com.sigaa.dto.HorarioResponse;
import com.sigaa.model.Horario;
import com.sigaa.repository.HorarioRepository;
import com.sigaa.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/horarios")
@PreAuthorize("hasRole('administrador')")
public class HorarioController {
    private final HorarioRepository repository;
    private final UsuarioRepository usuarioRepository;
    public HorarioController(HorarioRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }
    @GetMapping
    @Transactional(readOnly = true)
    public List<HorarioResponse> listar() {
        return repository.findAll().stream().map(HorarioResponse::from).toList();
    }
    @GetMapping("/usuario/{usuarioId}")
    @Transactional(readOnly = true)
    public List<HorarioResponse> listarPorUsuario(@PathVariable Long usuarioId) {
        return repository.findByUsuarioIdOrderByDiaAsc(usuarioId).stream().map(HorarioResponse::from).toList();
    }
    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    @Transactional
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body) {
        Horario h = new Horario();
        Long usuarioId = body.containsKey("usuarioId") ? Long.valueOf(body.get("usuarioId").toString()) : null;
        if (usuarioId == null) return ResponseEntity.badRequest().body("usuarioId es obligatorio");
        if (!isValidSchedule(body)) return ResponseEntity.badRequest().body("Horario inválido");
        var usuarioOpt = usuarioRepository.findById(usuarioId);
        if (usuarioOpt.isEmpty()) return ResponseEntity.badRequest().body("Usuario no encontrado");
        h.setUsuario(usuarioOpt.get());
        h.setDia(body.containsKey("dia") ? ((Number) body.get("dia")).intValue() : 1);
        h.setEntrada((String) body.getOrDefault("entrada", "08:00"));
        h.setSalida((String) body.getOrDefault("salida", "17:00"));
        h.setTolerancia(body.containsKey("tolerancia") ? ((Number) body.get("tolerancia")).intValue() : 15);
        h.setEstado((String) body.getOrDefault("estado", "normal"));
        return ResponseEntity.status(201).body(HorarioResponse.from(repository.save(h)));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    @Transactional
    public ResponseEntity<HorarioResponse> actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!isValidSchedule(body)) return ResponseEntity.badRequest().build();
        return repository.findById(id).map(h -> {
            if (body.containsKey("dia")) h.setDia(((Number) body.get("dia")).intValue());
            if (body.containsKey("entrada")) h.setEntrada((String) body.get("entrada"));
            if (body.containsKey("salida")) h.setSalida((String) body.get("salida"));
            if (body.containsKey("tolerancia")) h.setTolerancia(((Number) body.get("tolerancia")).intValue());
            if (body.containsKey("estado")) h.setEstado((String) body.get("estado"));
            return ResponseEntity.ok(HorarioResponse.from(repository.save(h)));
        }).orElse(ResponseEntity.notFound().build());
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (repository.existsById(id)) { repository.deleteById(id); return ResponseEntity.noContent().build(); }
        return ResponseEntity.notFound().build();
    }

    private boolean isValidSchedule(Map<String, Object> body) {
        try {
            if (body.containsKey("dia")) {
                int dia = ((Number) body.get("dia")).intValue();
                if (dia < 1 || dia > 7) return false;
            }
            if (body.containsKey("tolerancia") && ((Number) body.get("tolerancia")).intValue() < 0) return false;
            if (body.containsKey("entrada")) LocalTime.parse(String.valueOf(body.get("entrada")));
            if (body.containsKey("salida")) LocalTime.parse(String.valueOf(body.get("salida")));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
