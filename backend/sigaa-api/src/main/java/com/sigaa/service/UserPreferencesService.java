package com.sigaa.service;

import com.sigaa.util.LogDiagnostics;
import com.sigaa.dto.UpdatePreferencesRequest;
import com.sigaa.dto.UserPreferencesResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Usuario;
import com.sigaa.model.UserPreferences;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.UserPreferencesRepository;
import com.sigaa.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class UserPreferencesService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesService.class);

    private final UserPreferencesRepository preferencesRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditLogRepository auditLogRepository;

    public UserPreferencesService(UserPreferencesRepository preferencesRepository,
                                   UsuarioRepository usuarioRepository,
                                   AuditLogRepository auditLogRepository) {
        this.preferencesRepository = preferencesRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public UserPreferencesResponse getMyPreferences() {
        Long usuarioId = resolveUsuarioId();
        UserPreferences prefs = preferencesRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> createDefaultPreferences(usuarioId));
        return toResponse(prefs);
    }

    @Transactional
    public UserPreferencesResponse savePreferences(UpdatePreferencesRequest request) {
        Long usuarioId = resolveUsuarioId();
        UserPreferences prefs = preferencesRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> createDefaultPreferences(usuarioId));

        String oldValue = snapshotToString(prefs);
        applyRequest(prefs, request);
        UserPreferences saved = preferencesRepository.save(prefs);
        String newValue = snapshotToString(saved);

        logAudit("ACTUALIZAR_PREFERENCIAS", "UserPreferences", saved.getId(),
                "Preferencias actualizadas por usuario " + usuarioId
                + "\nAntes: " + oldValue
                + "\nDespues: " + newValue);
        return toResponse(saved);
    }

    @Transactional
    public UserPreferencesResponse resetPreferences() {
        Long usuarioId = resolveUsuarioId();
        UserPreferences prefs = preferencesRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> createDefaultPreferences(usuarioId));

        String oldValue = snapshotToString(prefs);
        prefs.setTheme("light");
        prefs.setLanguage("es");
        prefs.setTimezone("America/Panama");
        prefs.setDateFormat("DD/MM/YYYY");
        prefs.setTimeFormat("HH:mm");
        prefs.setHomePage("dashboard");
        prefs.setRecordsPerPage(20);
        prefs.setSidebarExpanded(true);
        prefs.setAnimationsEnabled(true);
        prefs.setEmailNotifications(true);
        prefs.setPushNotifications(true);
        prefs.setSoundEnabled(true);
        prefs.setConfirmationsEnabled(true);
        prefs.setCustomDashboard(null);
        prefs.setQuickAccessCards(null);
        prefs.setFontSize("medium");
        prefs.setHighContrast(false);

        UserPreferences saved = preferencesRepository.save(prefs);
        String newValue = snapshotToString(saved);

        logAudit("RESETEAR_PREFERENCIAS", "UserPreferences", saved.getId(),
                "Preferencias reseteadas por usuario " + usuarioId
                + "\nAntes: " + oldValue
                + "\nDespues: " + newValue);
        return toResponse(saved);
    }

    private UserPreferences createDefaultPreferences(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + usuarioId));

        UserPreferences prefs = new UserPreferences();
        prefs.setUsuario(usuario);
        return preferencesRepository.save(prefs);
    }

    private void applyRequest(UserPreferences prefs, UpdatePreferencesRequest req) {
        if (req.getTheme() != null) prefs.setTheme(req.getTheme());
        if (req.getLanguage() != null) prefs.setLanguage(req.getLanguage());
        if (req.getTimezone() != null) prefs.setTimezone(req.getTimezone());
        if (req.getDateFormat() != null) prefs.setDateFormat(req.getDateFormat());
        if (req.getTimeFormat() != null) prefs.setTimeFormat(req.getTimeFormat());
        if (req.getHomePage() != null) {
            validateHomePage(req.getHomePage());
            prefs.setHomePage(req.getHomePage());
        }
        if (req.getRecordsPerPage() != null) prefs.setRecordsPerPage(req.getRecordsPerPage());
        if (req.getSidebarExpanded() != null) prefs.setSidebarExpanded(req.getSidebarExpanded());
        if (req.getAnimationsEnabled() != null) prefs.setAnimationsEnabled(req.getAnimationsEnabled());
        if (req.getEmailNotifications() != null) prefs.setEmailNotifications(req.getEmailNotifications());
        if (req.getPushNotifications() != null) prefs.setPushNotifications(req.getPushNotifications());
        if (req.getSoundEnabled() != null) prefs.setSoundEnabled(req.getSoundEnabled());
        if (req.getConfirmationsEnabled() != null) prefs.setConfirmationsEnabled(req.getConfirmationsEnabled());
        if (req.getCustomDashboard() != null) prefs.setCustomDashboard(req.getCustomDashboard());
        if (req.getQuickAccessCards() != null) prefs.setQuickAccessCards(req.getQuickAccessCards());
        if (req.getFontSize() != null) prefs.setFontSize(req.getFontSize());
        if (req.getHighContrast() != null) prefs.setHighContrast(req.getHighContrast());
    }

    private UserPreferencesResponse toResponse(UserPreferences prefs) {
        UserPreferencesResponse resp = new UserPreferencesResponse();
        resp.setId(prefs.getId());
        resp.setUsuarioId(prefs.getUsuario() != null ? prefs.getUsuario().getId() : null);
        resp.setTheme(prefs.getTheme());
        resp.setLanguage(prefs.getLanguage());
        resp.setTimezone(prefs.getTimezone());
        resp.setDateFormat(prefs.getDateFormat());
        resp.setTimeFormat(prefs.getTimeFormat());
        resp.setHomePage(prefs.getHomePage());
        resp.setRecordsPerPage(prefs.getRecordsPerPage());
        resp.setSidebarExpanded(prefs.getSidebarExpanded());
        resp.setAnimationsEnabled(prefs.getAnimationsEnabled());
        resp.setEmailNotifications(prefs.getEmailNotifications());
        resp.setPushNotifications(prefs.getPushNotifications());
        resp.setSoundEnabled(prefs.getSoundEnabled());
        resp.setConfirmationsEnabled(prefs.getConfirmationsEnabled());
        resp.setCustomDashboard(prefs.getCustomDashboard());
        resp.setQuickAccessCards(prefs.getQuickAccessCards());
        resp.setFontSize(prefs.getFontSize());
        resp.setHighContrast(prefs.getHighContrast());
        resp.setCreatedAt(prefs.getCreatedAt());
        resp.setUpdatedAt(prefs.getUpdatedAt());
        return resp;
    }

    private String snapshotToString(UserPreferences prefs) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("theme", String.valueOf(prefs.getTheme()));
        map.put("language", String.valueOf(prefs.getLanguage()));
        map.put("timezone", String.valueOf(prefs.getTimezone()));
        map.put("dateFormat", String.valueOf(prefs.getDateFormat()));
        map.put("timeFormat", String.valueOf(prefs.getTimeFormat()));
        map.put("homePage", String.valueOf(prefs.getHomePage()));
        map.put("recordsPerPage", String.valueOf(prefs.getRecordsPerPage()));
        map.put("sidebarExpanded", String.valueOf(prefs.getSidebarExpanded()));
        map.put("animationsEnabled", String.valueOf(prefs.getAnimationsEnabled()));
        map.put("emailNotifications", String.valueOf(prefs.getEmailNotifications()));
        map.put("pushNotifications", String.valueOf(prefs.getPushNotifications()));
        map.put("soundEnabled", String.valueOf(prefs.getSoundEnabled()));
        map.put("confirmationsEnabled", String.valueOf(prefs.getConfirmationsEnabled()));
        map.put("fontSize", String.valueOf(prefs.getFontSize()));
        map.put("highContrast", String.valueOf(prefs.getHighContrast()));
        return map.toString();
    }

    private Long resolveUsuarioId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("No hay usuario autenticado");
        }
        String correo = auth.getName();
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + correo));
        return usuario.getId();
    }

    /** Keeps a preference from becoming a bypass to a route the user cannot access. */
    private void validateHomePage(String homePage) {
        Set<String> knownPages = Set.of("dashboard", "actas", "tareas", "reuniones", "documentos", "grupos", "inventario", "reporte");
        if (!knownPages.contains(homePage)) throw new IllegalArgumentException("Página inicial no válida");
        String correo = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByCorreo(correo)
            .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + correo));
        boolean admin = usuario.getRoles().stream().anyMatch(r -> "superadmin".equalsIgnoreCase(r.getNombre()));
        boolean jefe = usuario.getMembresias().stream().anyMatch(m -> m.getRol() != null && "jefe de unidad".equalsIgnoreCase(m.getRol().getNombre()));
        if (("inventario".equals(homePage) && !admin) || ("reporte".equals(homePage) && !admin && !jefe)) {
            throw new org.springframework.security.access.AccessDeniedException("No tiene acceso a la página inicial seleccionada");
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    return xff.split(",")[0].trim();
                }
                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isBlank()) {
                    return realIp;
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener la IP del cliente: {}", LogDiagnostics.describe(e));
        }
        return "unknown";
    }

    private void logAudit(String accion, String entidad, Long entidadId, String detalle) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String usuario = auth != null ? auth.getName() : "sistema";
            AuditLog auditLog = new AuditLog();
            auditLog.setUsuario(usuario);
            auditLog.setAccion(accion);
            auditLog.setEntidad(entidad);
            auditLog.setEntidadId(entidadId);
            auditLog.setDetalle(detalle);
            auditLog.setDireccionIp(getClientIp());
            auditLog.setFecha(LocalDateTime.now());
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Error al guardar log de auditoria para {} sobre {} id={}: {}",
                    accion, entidad, entidadId, LogDiagnostics.describe(e));
        }
    }
}
