package com.sigaa.service;

import com.sigaa.config.SecurityDeploymentProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TwoFactorService {
    private static final int CODE_EXPIRY_MINUTES = (int) (SecurityDeploymentProperties.MAX_TWO_FACTOR_TOKEN_EXPIRY_MS / 60_000);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, CodeEntry> codeCache = new ConcurrentHashMap<>();

    private final JavaMailSender mailSender;
    private final String mailHost;
    private final String mailFrom;

    public TwoFactorService(Optional<JavaMailSender> mailSender,
                            @Value("${spring.mail.host:}") String mailHost,
                            @Value("${MAIL_FROM:}") String mailFrom) {
        this.mailSender = mailSender.orElse(null);
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
    }

    public boolean isMailAvailable() { return mailSender != null && mailHost != null && !mailHost.isBlank(); }
    public String generateCode(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeCache.put(email, new CodeEntry(code, Instant.now().plusSeconds(CODE_EXPIRY_MINUTES * 60)));
        sendCodeEmail(email, code, "Código de verificación SIGAA", CODE_EXPIRY_MINUTES);
        return code;
    }
    public boolean verifyCode(String email, String code) {
        CodeEntry entry = codeCache.get(email);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) { codeCache.remove(email); return false; }
        boolean valid = entry.code().equals(code); if (valid) codeCache.remove(email); return valid;
    }
    public void sendPasswordResetEmail(String email, String code, int validMinutes) {
        if (mailSender == null) throw new IllegalStateException("El correo no está configurado");
        SimpleMailMessage message = newMessage(email, "Código para recuperar tu contraseña");
        message.setText("Hola,\n\nRecibimos una solicitud para restablecer tu contraseña.\n\nTu código de verificación es: " + code
                + "\n\nEste código vencerá en " + validMinutes + " minutos y solo puede utilizarse una vez.\n\nSi no realizaste esta solicitud, puedes ignorar este mensaje. No compartas este código con nadie.");
        mailSender.send(message);
    }
    public void sendPasswordChangedEmail(String email) {
        if (mailSender == null) return;
        SimpleMailMessage message = newMessage(email, "Tu contraseña fue actualizada");
        message.setText("Tu contraseña fue actualizada correctamente. Si no realizaste este cambio, contacta al administrador de inmediato.");
        mailSender.send(message);
    }
    private void sendCodeEmail(String email, String code, String subject, int minutes) {
        if (mailSender == null) throw new IllegalStateException("El correo no está configurado");
        SimpleMailMessage message = newMessage(email, subject); message.setText("Tu código es: " + code + "\n\nVálido por " + minutes + " minutos."); mailSender.send(message);
    }
    private SimpleMailMessage newMessage(String email, String subject) { SimpleMailMessage message = new SimpleMailMessage(); message.setTo(email); message.setSubject(subject); if (mailFrom != null && !mailFrom.isBlank()) message.setFrom(mailFrom); return message; }
    private record CodeEntry(String code, Instant expiresAt) { }
}
