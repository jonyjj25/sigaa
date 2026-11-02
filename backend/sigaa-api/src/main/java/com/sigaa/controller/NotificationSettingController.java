package com.sigaa.controller;

import com.sigaa.service.NotificationHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones/configuracion")
@PreAuthorize("hasRole('administrador')")
public class NotificationSettingController {

    private final NotificationHistoryService historyService;

    public NotificationSettingController(NotificationHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/global")
    public List<Map<String, Object>> obtenerConfigGlobal() {
        return historyService.getGlobalSettings();
    }

    @PutMapping("/global")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<Map<String, Object>>> actualizarConfigGlobal(@RequestBody Map<String, String> settings) {
        return ResponseEntity.ok(historyService.updateGlobalSettingsBulk(settings));
    }

    @PatchMapping("/global/{clave}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> actualizarConfigGlobalIndividual(@PathVariable String clave,
                                                                                 @RequestBody Map<String, String> body) {
        String valor = body.getOrDefault("valor", "");
        return ResponseEntity.ok(historyService.updateGlobalSetting(clave, valor));
    }

    @GetMapping("/historial")
    public List<Map<String, Object>> listarHistorial() {
        return historyService.findAllHistory();
    }

    @GetMapping("/estadisticas")
    public Map<String, Object> obtenerEstadisticas() {
        return historyService.getEstadisticas();
    }

    @GetMapping("/plantillas")
    public List<Map<String, Object>> listarPlantillas() {
        return historyService.findAllTemplates();
    }

    @GetMapping("/plantillas/{id}")
    public ResponseEntity<Map<String, Object>> obtenerPlantilla(@PathVariable Long id) {
        return ResponseEntity.ok(historyService.findTemplateById(id));
    }

    @PostMapping("/plantillas")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> crearPlantilla(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(historyService.createTemplate(body));
    }

    @PutMapping("/plantillas/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Map<String, Object>> actualizarPlantilla(@PathVariable Long id,
                                                                    @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(historyService.updateTemplate(id, body));
    }

    @DeleteMapping("/plantillas/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminarPlantilla(@PathVariable Long id) {
        historyService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
