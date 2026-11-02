package com.sigaa.service;

import com.sigaa.model.AuditLog;
import com.sigaa.model.PasswordResetRequest;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.PasswordResetRequestRepository;
import com.sigaa.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class PasswordResetService {
    private static final int CODE_EXPIRY_MINUTES = 10, RESET_TOKEN_EXPIRY_MINUTES = 10, MAX_CODE_ATTEMPTS = 5, MAX_REQUESTS_PER_15_MINUTES = 3, MIN_RESEND_SECONDS = 60;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final String GENERIC_MESSAGE = "Si el correo está registrado, recibirás un código de verificación.";
    private final UsuarioRepository usuarioRepository;
    private final PasswordResetRequestRepository resetRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityPasswordService securityPasswordService;
    private final PasswordHistoryService passwordHistoryService;
    private final TwoFactorService twoFactorService;
    private final AuditLogRepository auditLogRepository;

    public PasswordResetService(UsuarioRepository usuarioRepository, PasswordResetRequestRepository resetRepository, PasswordEncoder passwordEncoder,
                                SecurityPasswordService securityPasswordService, PasswordHistoryService passwordHistoryService,
                                TwoFactorService twoFactorService, AuditLogRepository auditLogRepository) {
        this.usuarioRepository = usuarioRepository; this.resetRepository = resetRepository; this.passwordEncoder = passwordEncoder;
        this.securityPasswordService = securityPasswordService; this.passwordHistoryService = passwordHistoryService; this.twoFactorService = twoFactorService; this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public String requestReset(String rawEmail, String ipAddress) {
        String email = normalizeEmail(rawEmail);
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Formato de correo inválido");
        if (!twoFactorService.isMailAvailable()) throw new ResetMailUnavailableException();
        Optional<Usuario> userOpt = usuarioRepository.findByCorreo(email);
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getActivo())) { audit("SOLICITAR_RESTABLECIMIENTO", null, email, ipAddress, "Solicitud recibida"); return GENERIC_MESSAGE; }
        Usuario user = userOpt.get(); LocalDateTime now = LocalDateTime.now();
        if (resetRepository.countByUsuarioIdAndCreatedAtAfter(user.getId(), now.minusMinutes(15)) >= MAX_REQUESTS_PER_15_MINUTES
                || (ipAddress != null && resetRepository.countByRequestIpAndCreatedAtAfter(ipAddress, now.minusMinutes(15)) >= MAX_REQUESTS_PER_15_MINUTES)) {
            audit("LIMITE_RESTABLECIMIENTO", user.getId(), email, ipAddress, "Límite de solicitudes superado"); throw new ResetRateLimitException();
        }
        resetRepository.findFirstByUsuarioIdAndUsedFalseAndBlockedFalseOrderByCreatedAtDesc(user.getId()).ifPresent(previous -> {
            if (previous.getCreatedAt().plusSeconds(MIN_RESEND_SECONDS).isAfter(now)) throw new ResetRateLimitException();
            previous.setUsed(true); previous.setUsedAt(now); resetRepository.save(previous);
        });
        String code = newCode(); PasswordResetRequest request = new PasswordResetRequest();
        request.setUsuario(user); request.setCodeHash(passwordEncoder.encode(code)); request.setExpiresAt(now.plusMinutes(CODE_EXPIRY_MINUTES)); request.setRequestIp(ipAddress);
        request = resetRepository.save(request);
        try { twoFactorService.sendPasswordResetEmail(email, code, CODE_EXPIRY_MINUTES); audit("ENVIAR_CODIGO_RESTABLECIMIENTO", user.getId(), email, ipAddress, "Código de recuperación enviado"); }
        catch (RuntimeException ex) { request.setBlocked(true); resetRepository.save(request); audit("FALLO_ENVIO_RESTABLECIMIENTO", user.getId(), email, ipAddress, "No se pudo entregar el correo"); throw new ResetMailUnavailableException(); }
        return GENERIC_MESSAGE;
    }

    @Transactional
    public VerificationResult verifyCode(String rawEmail, String code, String ipAddress) {
        String email = normalizeEmail(rawEmail);
        if (!EMAIL.matcher(email).matches() || code == null || !code.matches("\\d{6}")) return VerificationResult.invalid();
        Optional<Usuario> userOpt = usuarioRepository.findByCorreo(email);
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getActivo())) return VerificationResult.invalid();
        Usuario user = userOpt.get(); Optional<PasswordResetRequest> requestOpt = resetRepository.findFirstByUsuarioIdAndUsedFalseAndBlockedFalseOrderByCreatedAtDesc(user.getId());
        if (requestOpt.isEmpty()) return VerificationResult.invalid();
        PasswordResetRequest request = requestOpt.get(); LocalDateTime now = LocalDateTime.now();
        // Una repetición no debe invalidar el token emitido por la primera verificación.
        if (request.getCodeVerifiedAt() != null) return VerificationResult.invalid();
        if (!request.getExpiresAt().isAfter(now)) { request.setBlocked(true); resetRepository.save(request); audit("CODIGO_RESTABLECIMIENTO_EXPIRADO", user.getId(), email, ipAddress, "Código no utilizable"); return VerificationResult.expired(); }
        if (!passwordEncoder.matches(code, request.getCodeHash())) {
            request.setAttempts(request.getAttempts() + 1); if (request.getAttempts() >= MAX_CODE_ATTEMPTS) request.setBlocked(true); resetRepository.save(request);
            audit(request.isBlocked() ? "CODIGO_RESTABLECIMIENTO_BLOQUEADO" : "CODIGO_RESTABLECIMIENTO_INVALIDO", user.getId(), email, ipAddress, "Intento de verificación rechazado");
            return request.isBlocked() ? VerificationResult.blocked() : VerificationResult.invalid();
        }
        String token = newToken(); request.setCodeVerifiedAt(now); request.setResetTokenHash(passwordEncoder.encode(token)); request.setResetTokenExpiresAt(now.plusMinutes(RESET_TOKEN_EXPIRY_MINUTES)); resetRepository.save(request);
        audit("CODIGO_RESTABLECIMIENTO_VALIDADO", user.getId(), email, ipAddress, "Código validado"); return VerificationResult.success(token);
    }

    @Transactional
    public ResetResult resetPassword(String resetToken, String newPassword, String ipAddress) {
        if (resetToken == null || resetToken.isBlank()) return ResetResult.invalidToken();
        var candidate = resetRepository.findByCodeVerifiedAtIsNotNullAndUsedFalseAndBlockedFalseAndResetTokenExpiresAtAfter(LocalDateTime.now()).stream()
                .filter(item -> item.getResetTokenHash() != null && passwordEncoder.matches(resetToken, item.getResetTokenHash())).findFirst().orElse(null);
        if (candidate == null) return ResetResult.invalidToken();
        // Bloquear solo la solicitud coincidente y volver a validar su estado vigente.
        var requestOpt = resetRepository.findByIdForUpdate(candidate.getId());
        if (requestOpt.isEmpty()) return ResetResult.invalidToken();
        PasswordResetRequest request = requestOpt.get();
        LocalDateTime now = LocalDateTime.now();
        if (request.isUsed() || request.isBlocked() || request.getCodeVerifiedAt() == null
                || request.getResetTokenExpiresAt() == null || !request.getResetTokenExpiresAt().isAfter(now)
                || !Objects.equals(candidate.getResetTokenHash(), request.getResetTokenHash())) {
            return ResetResult.invalidToken();
        }
        Usuario user = request.getUsuario(); var validation = securityPasswordService.validate(newPassword);
        if (!validation.valid()) return ResetResult.weakPassword(String.join(". ", validation.errors()));
        if (!newPassword.equals(newPassword.trim())) return ResetResult.weakPassword("La contraseña no puede tener espacios al inicio o al final");
        if (passwordEncoder.matches(newPassword, user.getPassword())) return ResetResult.weakPassword("La nueva contraseña debe ser diferente a la actual");
        try { passwordHistoryService.changePassword(user, newPassword); }
        catch (IllegalArgumentException exception) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResetResult.weakPassword("No puede reutilizar una contraseña reciente");
        }
        user.setIntentosFallidos(0); user.setBloqueado(false); user.setBloqueadoHasta(null); usuarioRepository.save(user);
        request.setUsed(true); request.setUsedAt(now); request.setResetTokenHash(null); resetRepository.save(request);
        audit("RESTABLECIMIENTO_CONTRASENA_EXITOSO", user.getId(), user.getCorreo(), ipAddress, "Contraseña actualizada");
        String email = user.getCorreo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { twoFactorService.sendPasswordChangedEmail(email); } catch (RuntimeException ignored) { }
            }
        });
        return ResetResult.success();
    }
    private String normalizeEmail(String email) { return email == null ? "" : email.trim().toLowerCase(); }
    private String newCode() { return String.format("%06d", RANDOM.nextInt(1_000_000)); }
    private String newToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private void audit(String action, Long userId, String email, String ip, String detail) { try { AuditLog log = new AuditLog(); log.setUsuario(mask(email)); log.setAccion(action); log.setEntidad("PasswordReset"); log.setEntidadId(userId); log.setDireccionIp(ip); log.setDetalle(detail); auditLogRepository.save(log); } catch (RuntimeException ignored) { } }
    private String mask(String email) { int at = email == null ? -1 : email.indexOf('@'); return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at); }
    public record VerificationResult(Status status, String resetToken) { static VerificationResult success(String token) { return new VerificationResult(Status.SUCCESS, token); } static VerificationResult invalid() { return new VerificationResult(Status.INVALID, null); } static VerificationResult expired() { return new VerificationResult(Status.EXPIRED, null); } static VerificationResult blocked() { return new VerificationResult(Status.BLOCKED, null); } }
    public record ResetResult(Status status, String message) { static ResetResult success() { return new ResetResult(Status.SUCCESS, null); } static ResetResult invalidToken() { return new ResetResult(Status.INVALID, "La sesión de recuperación no es válida o expiró"); } static ResetResult weakPassword(String message) { return new ResetResult(Status.WEAK_PASSWORD, message); } }
    public enum Status { SUCCESS, INVALID, EXPIRED, BLOCKED, WEAK_PASSWORD }
    public static class ResetRateLimitException extends RuntimeException { }
    public static class ResetMailUnavailableException extends RuntimeException { }
}
