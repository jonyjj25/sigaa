package com.sigaa.service;

import com.sigaa.model.NotificationGlobalSetting;
import com.sigaa.model.NotificationHistory;
import com.sigaa.model.NotificationTemplate;
import com.sigaa.repository.NotificationGlobalSettingRepository;
import com.sigaa.repository.NotificationHistoryRepository;
import com.sigaa.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationHistoryService {

    private final NotificationHistoryRepository historyRepository;
    private final NotificationGlobalSettingRepository globalSettingRepository;
    private final NotificationTemplateRepository templateRepository;

    public NotificationHistoryService(NotificationHistoryRepository historyRepository,
                                      NotificationGlobalSettingRepository globalSettingRepository,
                                      NotificationTemplateRepository templateRepository) {
        this.historyRepository = historyRepository;
        this.globalSettingRepository = globalSettingRepository;
        this.templateRepository = templateRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllHistory() {
        return historyRepository.findAllByOrderByCreatedAtDesc().stream().map(this::historyToMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEstadisticas() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEnviados", historyRepository.countByEstado("enviado"));
        stats.put("totalPendientes", historyRepository.countByEstado("pendiente"));
        stats.put("totalErrores", historyRepository.countByEstado("error"));

        LocalDateTime hoyInicio = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime hoyFin = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        stats.put("enviadosHoy", historyRepository.countEnviadosEntre(hoyInicio, hoyFin));

        LocalDateTime semanaInicio = hoyInicio.minusDays(7);
        stats.put("enviadosSemana", historyRepository.countEnviadosEntre(semanaInicio, hoyFin));

        stats.put("porCorreo", historyRepository.countByCanal("Correo Electrónico"));
        stats.put("porPush", historyRepository.countByCanal("Push"));
        stats.put("porSMS", historyRepository.countByCanal("SMS"));
        stats.put("porSistema", historyRepository.countByCanal("Sistema"));

        return stats;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGlobalSettings() {
        return globalSettingRepository.findAllByOrderByClaveAsc().stream().map(this::globalSettingToMap).toList();
    }

    @Transactional
    public Map<String, Object> updateGlobalSetting(String clave, String valor) {
        NotificationGlobalSetting entity = globalSettingRepository.findByClave(clave)
            .orElseThrow(() -> new IllegalArgumentException("Configuración global no encontrada: " + clave));
        entity.setValor(valor);
        globalSettingRepository.save(entity);
        return globalSettingToMap(entity);
    }

    @Transactional
    public List<Map<String, Object>> updateGlobalSettingsBulk(Map<String, String> settings) {
        settings.forEach((clave, valor) -> {
            NotificationGlobalSetting entity = globalSettingRepository.findByClave(clave).orElse(null);
            if (entity != null) {
                entity.setValor(valor);
                globalSettingRepository.save(entity);
            }
        });
        return getGlobalSettings();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllTemplates() {
        return templateRepository.findAllByOrderByNombreAsc().stream().map(this::templateToMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findTemplateById(Long id) {
        NotificationTemplate t = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada con id: " + id));
        return templateToMap(t);
    }

    @Transactional
    public Map<String, Object> createTemplate(Map<String, Object> body) {
        NotificationTemplate t = new NotificationTemplate();
        applyTemplateBody(t, body);
        return templateToMap(templateRepository.save(t));
    }

    @Transactional
    public Map<String, Object> updateTemplate(Long id, Map<String, Object> body) {
        NotificationTemplate t = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada con id: " + id));
        applyTemplateBody(t, body);
        return templateToMap(templateRepository.save(t));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new IllegalArgumentException("Plantilla no encontrada con id: " + id);
        }
        templateRepository.deleteById(id);
    }

    private void applyTemplateBody(NotificationTemplate t, Map<String, Object> body) {
        if (body.containsKey("nombre")) t.setNombre((String) body.get("nombre"));
        if (body.containsKey("tipo")) t.setTipo((String) body.get("tipo"));
        if (body.containsKey("asunto")) t.setAsunto((String) body.get("asunto"));
        if (body.containsKey("cuerpo")) t.setCuerpo((String) body.get("cuerpo"));
        if (body.containsKey("activo")) t.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
        if (body.containsKey("idioma")) t.setIdioma((String) body.get("idioma"));
        if (body.containsKey("variables")) t.setVariables((String) body.get("variables"));
    }

    private Map<String, Object> historyToMap(NotificationHistory h) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(h.getId()));
        m.put("eventoCodigo", h.getEventoCodigo());
        m.put("canal", h.getCanal());
        m.put("destinatario", h.getDestinatario());
        m.put("asunto", h.getAsunto());
        m.put("mensaje", h.getMensaje());
        m.put("estado", h.getEstado());
        m.put("enviadoEn", h.getEnviadoEn() != null ? h.getEnviadoEn().toString() : null);
        m.put("errorDetalle", h.getErrorDetalle());
        m.put("createdAt", h.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> globalSettingToMap(NotificationGlobalSetting s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("clave", s.getClave());
        m.put("valor", s.getValor());
        m.put("descripcion", s.getDescripcion());
        m.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> templateToMap(NotificationTemplate t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(t.getId()));
        m.put("nombre", t.getNombre());
        m.put("tipo", t.getTipo());
        m.put("asunto", t.getAsunto());
        m.put("cuerpo", t.getCuerpo());
        m.put("activo", t.getActivo());
        m.put("idioma", t.getIdioma());
        m.put("variables", t.getVariables());
        m.put("eventoId", t.getEvento() != null ? t.getEvento().getId() : null);
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        m.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null);
        return m;
    }
}
