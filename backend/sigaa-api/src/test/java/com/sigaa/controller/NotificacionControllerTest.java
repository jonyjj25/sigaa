package com.sigaa.controller;

import com.sigaa.model.Notificacion;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotificacionRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.NotificationChannelRepository;
import com.sigaa.repository.NotificationEventRepository;
import com.sigaa.repository.NotificationSettingRepository;
import com.sigaa.service.InternalNotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificacionControllerTest {

    @Test
    void panelIncludesReadNotificationsFromTheLastTwoDays() {
        NotificacionRepository notifications = mock(NotificacionRepository.class);
        UsuarioRepository users = mock(UsuarioRepository.class);
        Authentication auth = mock(Authentication.class);
        Usuario user = new Usuario();
        user.setId(15L);

        Notificacion read = new Notificacion();
        read.setId(8L);
        read.setUsuario(user);
        read.setTitulo("Reunión actualizada");
        read.setMensaje("La reunión cambió");
        read.setLeida(true);
        read.setCreatedAt(LocalDateTime.now().minusHours(6));

        when(auth.getName()).thenReturn("usuario@example.test");
        when(users.findIdByCorreo("usuario@example.test")).thenReturn(Optional.of(user.getId()));
        when(notifications.findByUsuarioIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                eq(15L), any(LocalDateTime.class))).thenReturn(List.of(read));

        LocalDateTime before = LocalDateTime.now().minusDays(2).minusSeconds(1);
        var result = controller(notifications, users).misNotificaciones(auth);
        LocalDateTime after = LocalDateTime.now().minusDays(2).plusSeconds(1);

        assertEquals(List.of(java.util.Map.of("id", "8", "title", "Reunión actualizada", "message", "La reunión cambió",
                "type", "info", "timestamp", read.getCreatedAt().toString(), "read", true)), result);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notifications).findByUsuarioIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                eq(15L), cutoff.capture());
        assertTrue(cutoff.getValue().isAfter(before));
        assertTrue(cutoff.getValue().isBefore(after));
    }

    @Test
    void readingAnotherUsersNotificationIsForbiddenWithoutWriting() {
        NotificacionRepository notifications = mock(NotificacionRepository.class);
        UsuarioRepository users = mock(UsuarioRepository.class);
        Authentication auth = mock(Authentication.class);
        Usuario owner = new Usuario(); owner.setId(15L);
        Usuario other = new Usuario(); other.setId(16L);
        Notificacion notification = new Notificacion(); notification.setId(8L); notification.setUsuario(owner); notification.setLeida(false);
        when(auth.getName()).thenReturn("otro@example.test");
        when(users.findIdByCorreo("otro@example.test")).thenReturn(Optional.of(other.getId()));
        when(notifications.findById(8L)).thenReturn(Optional.of(notification));
        assertEquals(HttpStatus.FORBIDDEN, controller(notifications, users).marcarComoLeida(8L, auth).getStatusCode());
        assertFalse(notification.getLeida());
        verify(notifications, never()).save(any());
    }

    @Test
    void readingAnOwnedNotificationChangesOnlyItsReadState() {
        NotificacionRepository notifications = mock(NotificacionRepository.class);
        UsuarioRepository users = mock(UsuarioRepository.class);
        Authentication auth = mock(Authentication.class);
        Usuario owner = new Usuario(); owner.setId(15L);
        Notificacion notification = new Notificacion(); notification.setId(8L); notification.setUsuario(owner);
        notification.setLeida(false); notification.setMensaje("Mensaje original");
        when(auth.getName()).thenReturn("usuario@example.test");
        when(users.findIdByCorreo("usuario@example.test")).thenReturn(Optional.of(owner.getId()));
        when(notifications.findById(8L)).thenReturn(Optional.of(notification));
        assertEquals(HttpStatus.OK, controller(notifications, users).marcarComoLeida(8L, auth).getStatusCode());
        assertTrue(notification.getLeida()); assertEquals("Mensaje original", notification.getMensaje());
        assertSame(owner, notification.getUsuario()); verify(notifications).save(notification);
    }

    @Test
    void readingAllUpdatesOnlyTheCurrentUsersPendingNotifications() {
        NotificacionRepository notifications = mock(NotificacionRepository.class);
        UsuarioRepository users = mock(UsuarioRepository.class);
        Authentication auth = mock(Authentication.class);
        Usuario owner = new Usuario(); owner.setId(15L);
        Notificacion first = new Notificacion(); first.setUsuario(owner); first.setLeida(false);
        Notificacion second = new Notificacion(); second.setUsuario(owner); second.setLeida(false);
        when(auth.getName()).thenReturn("usuario@example.test");
        when(users.findIdByCorreo("usuario@example.test")).thenReturn(Optional.of(owner.getId()));
        when(notifications.findByUsuarioIdAndLeidaFalseOrderByCreatedAtDesc(15L)).thenReturn(List.of(first, second));
        assertEquals(HttpStatus.OK, controller(notifications, users).marcarTodasComoLeidas(auth).getStatusCode());
        assertTrue(first.getLeida()); assertTrue(second.getLeida());
        verify(notifications).saveAll(List.of(first, second));
        verify(notifications, never()).findByUsuarioIdOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void aMissingAccountCannotMarkNotificationsAndAMissingNotificationReturnsNotFound() {
        NotificacionRepository notifications = mock(NotificacionRepository.class);
        UsuarioRepository users = mock(UsuarioRepository.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ausente@example.test");
        var controller = controller(notifications, users);
        assertEquals(HttpStatus.FORBIDDEN, controller.marcarTodasComoLeidas(auth).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.marcarComoLeida(8L, auth).getStatusCode());
        assertTrue(controller.misNotificaciones(auth).isEmpty());
        verify(notifications).findById(8L);
        verifyNoMoreInteractions(notifications);
    }

    private NotificacionController controller(NotificacionRepository notifications, UsuarioRepository users) {
        return new NotificacionController(notifications, users, new InternalNotificationService(notifications,
                mock(NotificationChannelRepository.class), mock(NotificationEventRepository.class), mock(NotificationSettingRepository.class)));
    }
}
