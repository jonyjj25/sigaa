package com.sigaa.service;

import com.sigaa.model.AdminPasswordResetRequest;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AdminPasswordResetRequestRepository;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminPasswordResetService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private final SecureRandom random = new SecureRandom();
    private final AdminPasswordResetRequestRepository requests;
    private final UsuarioRepository users;
    private final PasswordHistoryService passwordHistory;
    private final SecurityPasswordService passwordPolicy;
    private final SecurityPolicyService policies;
    private final InternalNotificationService notifications;
    private final AuditLogRepository audit;

    public AdminPasswordResetService(AdminPasswordResetRequestRepository requests, UsuarioRepository users,
                                     PasswordHistoryService passwordHistory, SecurityPasswordService passwordPolicy,
                                     SecurityPolicyService policies, InternalNotificationService notifications,
                                     AuditLogRepository audit) {
        this.requests = requests; this.users = users; this.passwordHistory = passwordHistory;
        this.passwordPolicy = passwordPolicy; this.policies = policies; this.notifications = notifications; this.audit = audit;
    }

    @Transactional
    public void request(String email, String ip) {
        if (email == null || email.isBlank()) return;
        if (requests.countByRequestIpAndRequestedAtAfter(ip, LocalDateTime.now().minusMinutes(15)) >= 5) return;
        users.findByCorreoIgnoreCase(email.trim()).filter(u -> Boolean.TRUE.equals(u.getActivo())).ifPresent(user -> {
            if (requests.findByUsuarioIdAndStatus(user.getId(), "PENDIENTE").isPresent()) return;
            AdminPasswordResetRequest request = new AdminPasswordResetRequest();
            request.setUsuario(user); request.setRequestIp(ip); requests.save(request);
            for (Usuario admin : users.findActiveAdministrators()) {
                notifications.send("password_reset_requested", admin, "Solicitud de restablecimiento",
                        user.getNombre() + " " + user.getApellido() + " solicitó restablecer su contraseña. Revísala en Configuración > Usuarios.");
            }
            audit("SOLICITAR_RESTABLECIMIENTO", user, "Solicitud interna creada", email, ip);
        });
    }

    @Transactional(readOnly = true)
    public List<RequestView> pending() {
        return requests.findByStatusOrderByRequestedAtDesc("PENDIENTE").stream().map(this::view).toList();
    }

    @Transactional
    public ApprovalResult approve(Long id, String adminEmail, String ip) {
        AdminPasswordResetRequest request = pendingRequest(id);
        Usuario admin = users.findByCorreoIgnoreCase(adminEmail).orElseThrow();
        Usuario user = request.getUsuario();
        String temporaryPassword = generateTemporaryPassword();
        passwordHistory.changePassword(user, temporaryPassword);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
        user.setCambioContrasenaRequerido(true);
        user.setContrasenaTemporalExpiraEn(expiresAt);
        user.setIntentosFallidos(0); user.setBloqueado(false); user.setBloqueadoHasta(null);
        users.save(user);
        request.setStatus("APROBADA"); request.setResolvedAt(LocalDateTime.now()); request.setResolvedBy(admin);
        request.setTemporaryPasswordExpiresAt(expiresAt); requests.save(request);
        audit("APROBAR_RESTABLECIMIENTO", user, "Contraseña temporal emitida; sesiones cerradas", adminEmail, ip);
        return new ApprovalResult(temporaryPassword, expiresAt, user.getNombre() + " " + user.getApellido(), user.getCorreo());
    }

    @Transactional
    public void reject(Long id, String adminEmail, String reason, String ip) {
        AdminPasswordResetRequest request = pendingRequest(id);
        Usuario admin = users.findByCorreoIgnoreCase(adminEmail).orElseThrow();
        request.setStatus("RECHAZADA"); request.setResolvedAt(LocalDateTime.now()); request.setResolvedBy(admin);
        request.setRejectionReason(reason == null ? null : reason.trim()); requests.save(request);
        audit("RECHAZAR_RESTABLECIMIENTO", request.getUsuario(), "Solicitud rechazada: " + request.getRejectionReason(), adminEmail, ip);
    }

    private AdminPasswordResetRequest pendingRequest(Long id) {
        AdminPasswordResetRequest request = requests.findById(id).orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        if (!"PENDIENTE".equals(request.getStatus())) throw new IllegalArgumentException("La solicitud ya fue atendida");
        return request;
    }

    private String generateTemporaryPassword() {
        int length = Math.min(policies.getPasswordMaxLength(), Math.max(16, policies.getPasswordMinLength()));
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder value = new StringBuilder(length);
            for (int i = 0; i < length; i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            if (passwordPolicy.validate(value.toString()).valid()) return value.toString();
        }
        throw new IllegalStateException("No se pudo generar una contraseña compatible con la política actual");
    }

    private RequestView view(AdminPasswordResetRequest r) {
        Usuario u = r.getUsuario();
        return new RequestView(r.getId(), u.getId(), u.getNombre() + " " + u.getApellido(), u.getCorreo(),
                r.getRequestIp(), r.getRequestedAt(), r.getStatus());
    }

    private void audit(String action, Usuario target, String detail, String actor, String ip) {
        AuditLog log = new AuditLog(); log.setUsuario(actor); log.setAccion(action); log.setEntidad("Usuario");
        log.setEntidadId(target.getId()); log.setDetalle(detail); log.setDireccionIp(ip); audit.save(log);
    }

    public record RequestView(Long id, Long userId, String userName, String email, String requestIp,
                              LocalDateTime requestedAt, String status) {}
    public record ApprovalResult(String temporaryPassword, LocalDateTime expiresAt, String userName, String email) {}
}
