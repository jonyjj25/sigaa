package com.sigaa.service;

import com.sigaa.model.Reporte;
import com.sigaa.model.ReporteProgramacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ReporteDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(ReporteDeliveryService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final JavaMailSender mailSender;
    private final String mailHost;
    private final String mailFrom;

    public ReporteDeliveryService(Optional<JavaMailSender> mailSender,
                                  @Value("${spring.mail.host:}") String mailHost,
                                  @Value("${MAIL_FROM:}") String mailFrom) {
        this.mailSender = mailSender.orElse(null);
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
    }

    public boolean notificar(ReporteProgramacion programacion, Reporte reporte) {
        if (programacion.getDestinatarios() == null || programacion.getDestinatarios().isBlank()) return true;
        String[] recipients = Arrays.stream(programacion.getDestinatarios().split("[,;\\n]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> EMAIL.matcher(value).matches())
                .distinct()
                .toArray(String[]::new);
        if (recipients.length == 0 || mailSender == null || mailHost == null || mailHost.isBlank()) return false;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipients);
            if (mailFrom != null && !mailFrom.isBlank()) message.setFrom(mailFrom);
            message.setSubject("SIGAA · Reporte disponible: " + reporte.getNombre());
            message.setText("El reporte programado “" + reporte.getNombre() + "” fue generado correctamente. "
                    + "Ingresa a SIGAA > Reportes para consultarlo o descargar sus archivos.");
            mailSender.send(message);
            return true;
        } catch (RuntimeException exception) {
            log.warn("No se pudo notificar la generación del reporte programado id={}", programacion.getId());
            return false;
        }
    }
}
