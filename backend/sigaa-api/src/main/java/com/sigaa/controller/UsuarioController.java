package com.sigaa.controller;

import com.sigaa.dto.UsuarioRequest;
import com.sigaa.dto.UsuarioResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final AuditLogRepository auditLogRepository;

    public UsuarioController(UsuarioService usuarioService, AuditLogRepository auditLogRepository) {
        this.usuarioService = usuarioService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    @GetMapping("/seleccionables")
    @PreAuthorize("hasAnyAuthority('Reuniones:Gestionar participantes','Reuniones:Generar acta','Actas:Crear','Actas:Editar','Tareas:Asignar','Grupos:Gestionar miembros','Documentos:Ver','Notas:Crear','Notas:Editar','Memos:Crear','Memos:Dar seguimiento')")
    public List<UsuarioResponse> listarSeleccionables() {
        return usuarioService.listarSeleccionables();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public ResponseEntity<UsuarioResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public List<UsuarioResponse> buscar(@RequestParam String q) {
        return usuarioService.buscar(q);
    }

    @GetMapping("/activos")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public List<UsuarioResponse> listarActivos() {
        return usuarioService.listarActivos();
    }

    @GetMapping("/inactivos")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public List<UsuarioResponse> listarInactivos() {
        return usuarioService.listarInactivos();
    }

    @GetMapping("/bloqueados")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public List<UsuarioResponse> listarBloqueados() {
        return usuarioService.listarBloqueados();
    }

    @GetMapping("/departamento/{departamento}")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public List<UsuarioResponse> listarPorDepartamento(@PathVariable String departamento) {
        return usuarioService.listarPorDepartamento(departamento);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public Map<String, Object> estadisticas() {
        return usuarioService.estadisticas();
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('Usuarios:Ver')")
    public List<AuditLog> historial(@PathVariable Long id) {
        return auditLogRepository.findByEntidadAndEntidadIdOrderByFechaDesc("Usuario", id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Usuarios:Crear')")
    public ResponseEntity<UsuarioResponse> crear(@RequestBody UsuarioRequest request) {
        return ResponseEntity.status(201).body(usuarioService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('Usuarios:Editar')")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id, @RequestBody UsuarioRequest request) {
        return ResponseEntity.ok(usuarioService.actualizar(id, request));
    }

    @PatchMapping("/{id}/toggle-block")
    @PreAuthorize("hasAuthority('Usuarios:Editar')")
    public ResponseEntity<UsuarioResponse> toggleBlock(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.toggleBlock(id));
    }

    @PatchMapping("/{id}/toggle-activo")
    @PreAuthorize("hasAuthority('Usuarios:Editar')")
    public ResponseEntity<UsuarioResponse> toggleActivo(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.toggleActivo(id));
    }

    @PatchMapping("/{id}/cambiar-contrasena")
    @PreAuthorize("hasAuthority('Usuarios:Editar')")
    public ResponseEntity<UsuarioResponse> cambiarContrasena(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(usuarioService.cambiarContrasena(id, body.get("nuevaContrasena")));
    }

    @PatchMapping("/{id}/forzar-cierre-sesion")
    @PreAuthorize("hasAuthority('Usuarios:Editar')")
    public ResponseEntity<UsuarioResponse> forzarCierreSesion(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.forzarCierreSesion(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('Usuarios:Eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanente")
    @PreAuthorize("hasRole('superadmin')")
    public ResponseEntity<Void> eliminarPermanente(@PathVariable Long id) {
        usuarioService.eliminarDefinitivo(id);
        return ResponseEntity.noContent().build();
    }
}
