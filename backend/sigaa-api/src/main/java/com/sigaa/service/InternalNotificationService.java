package com.sigaa.service;

import com.sigaa.model.Notificacion;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotificacionRepository;
import com.sigaa.repository.NotificationChannelRepository;
import com.sigaa.repository.NotificationEventRepository;
import com.sigaa.repository.NotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.List;

/** Gestiona las notificaciones internas y respeta la configuración de sus envíos. */
@Service
public class InternalNotificationService {
    private final NotificacionRepository notifications;
    private final NotificationChannelRepository channels;
    private final NotificationEventRepository events;
    private final NotificationSettingRepository settings;

    public InternalNotificationService(NotificacionRepository notifications, NotificationChannelRepository channels,
                                       NotificationEventRepository events, NotificationSettingRepository settings) {
        this.notifications = notifications;
        this.channels = channels;
        this.events = events;
        this.settings = settings;
    }

    @Transactional
    public void send(String eventCode, Usuario recipient, String title, String message) {
        if (recipient == null || !isEnabled(eventCode)) return;
        Notificacion notification = new Notificacion();
        notification.setUsuario(recipient);
        notification.setTitulo(title);
        notification.setMensaje(message);
        notifications.save(notification);
    }

    /** Solo para callbacks de operaciones ya confirmadas; no reutiliza su transacción terminada. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAfterCommit(String eventCode, Usuario recipient, String title, String message) {
        send(eventCode, recipient, title, message);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> recientes(Long usuarioId) {
        // Leer una notificación no la retira del historial reciente del panel.
        return notifications.findByUsuarioIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                usuarioId, LocalDateTime.now().minusDays(2));
    }

    @Transactional
    public void marcarTodasComoLeidas(Long usuarioId) {
        List<Notificacion> pendientes = notifications.findByUsuarioIdAndLeidaFalseOrderByCreatedAtDesc(usuarioId);
        pendientes.forEach(n -> n.setLeida(true));
        notifications.saveAll(pendientes);
    }

    @Transactional
    public void marcarComoLeida(Notificacion notificacion) {
        notificacion.setLeida(true);
        notifications.save(notificacion);
    }

    private boolean isEnabled(String eventCode) {
        if (channels.findByNombre("Sistema").map(c -> !c.getActivo()).orElse(false)) return false;
        return events.findByCodigo(eventCode).map(event -> event.getActivo()
            && settings.findByEventoIdAndCanal(event.getId(), "SISTEMA").map(s -> s.getActivo()).orElse(true)
        ).orElse(true);
    }
}
