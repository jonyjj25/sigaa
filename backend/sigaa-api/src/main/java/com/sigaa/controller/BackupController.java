package com.sigaa.controller;

import com.sigaa.model.Backup;
import com.sigaa.dto.BackupOperationalStatusResponse;
import com.sigaa.dto.BackupResponse;
import com.sigaa.service.BackupService;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backups")
public class BackupController {
    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion:Ver:Respaldo')")
    public List<BackupResponse> listar() {
        return backupService.list().stream().map(backupService::toResponse).toList();
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion:Ver:Respaldo')")
    public BackupOperationalStatusResponse estadoOperativo() {
        return backupService.operationalStatus();
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion:Crear:Respaldo') or hasAuthority('Configuracion:Administrar:Respaldo')")
    public ResponseEntity<?> crear(Authentication authentication) {
        try {
            return ResponseEntity.status(201).body(backupService.toResponse(
                    backupService.create("manual", authentication.getName())));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion:Administrar:Respaldo')")
    public ResponseEntity<UrlResource> descargar(@PathVariable Long id) {
        Backup backup = backupService.get(id);
        UrlResource resource = backupService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + backup.getNombre().replace("\"", "") + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/restore-test")
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion:Administrar:Respaldo')")
    public Map<String, Object> probarRestauracion(@PathVariable Long id, Authentication authentication) {
        return backupService.testRestore(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador') or hasAuthority('Configuracion:Eliminar:Respaldo')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        backupService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
