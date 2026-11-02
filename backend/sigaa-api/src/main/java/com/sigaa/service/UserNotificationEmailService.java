package com.sigaa.service;

import com.sigaa.model.Usuario;
import com.sigaa.model.UserPreferences;
import com.sigaa.repository.UserPreferencesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Sends optional email copies of in-app notifications without changing their delivery flow. */
@Service
public class UserNotificationEmailService {
    private final UserPreferencesRepository preferences;

    private final JavaMailSender mailSender;
    private final String mailHost;

    public UserNotificationEmailService(UserPreferencesRepository preferences,
                                        Optional<JavaMailSender> mailSender,
                                        @Value("${spring.mail.host:}") String mailHost) {
        this.preferences = preferences;
        this.mailSender = mailSender.orElse(null);
        this.mailHost = mailHost;
    }

    public void sendIfEnabled(Usuario usuario, String subject, String message) {
        UserPreferences prefs = preferences.findByUsuarioId(usuario.getId()).orElse(null);
        if (prefs != null && Boolean.FALSE.equals(prefs.getEmailNotifications())) return;
        if (mailSender == null || mailHost == null || mailHost.isBlank() || usuario.getCorreo() == null || usuario.getCorreo().isBlank()) return;
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(usuario.getCorreo()); email.setSubject(subject); email.setText(message);
            mailSender.send(email);
        } catch (Exception ignored) {
            // The in-app notification has already been stored; mail must not break the business action.
        }
    }
}
