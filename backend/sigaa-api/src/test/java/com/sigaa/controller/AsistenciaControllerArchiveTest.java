package com.sigaa.controller;

import com.sigaa.model.AsistenciaSesion;
import com.sigaa.repository.ActaRepository;
import com.sigaa.repository.AsistenciaInvitadoRepository;
import com.sigaa.repository.AsistenciaRegistroRepository;
import com.sigaa.repository.AsistenciaSesionRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.AsistenciaAccessService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AsistenciaControllerArchiveTest {

    private final AsistenciaRegistroRepository registros = mock(AsistenciaRegistroRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AsistenciaSesionRepository sesiones = mock(AsistenciaSesionRepository.class);
    private final AsistenciaInvitadoRepository invitados = mock(AsistenciaInvitadoRepository.class);
    private final ActaRepository actas = mock(ActaRepository.class);
    private final AsistenciaAccessService acceso = mock(AsistenciaAccessService.class);
    private final AsistenciaController controller = new AsistenciaController(
            registros, usuarios, sesiones, invitados, actas, acceso);

    @BeforeEach void permitirAccesoParaProbarElArchivado() {
        when(acceso.puedeAcceder(any())).thenReturn(true);
    }

    @Test
    void archivesPastSessionsBeforeReturningTheArchive() {
        AsistenciaSesion antigua = sesion(7L, LocalDate.now().minusDays(1));
        when(sesiones.findByArchivadoFalseAndFechaBefore(LocalDate.now())).thenReturn(List.of(antigua));
        when(sesiones.findByArchivadoTrueOrderByFechaDescCreadoEnDesc()).thenReturn(List.of(antigua));

        var result = controller.listarSesiones(true);

        assertTrue(Boolean.TRUE.equals(antigua.getArchivado()));
        assertFalse(Boolean.TRUE.equals(antigua.getActivo()));
        assertNotNull(antigua.getArchivadoEn());
        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.getFirst().get("archivado"));
        verify(sesiones).saveAll(List.of(antigua));
    }

    @Test
    void manualArchiveClosesQrAndKeepsSession() {
        AsistenciaSesion sesion = sesion(8L, LocalDate.now());
        when(sesiones.findById(8L)).thenReturn(Optional.of(sesion));
        when(sesiones.save(any(AsistenciaSesion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.archivarSesion(8L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(Boolean.TRUE.equals(sesion.getActivo()));
        assertTrue(Boolean.TRUE.equals(sesion.getArchivado()));
        assertNotNull(sesion.getArchivadoEn());
        assertEquals(Boolean.TRUE, response.getBody().get("archivado"));
        verify(sesiones, never()).delete(any());
    }

    @Test
    void oldArchivedSessionCannotBeRestoredToCurrentSessions() {
        AsistenciaSesion antigua = sesion(9L, LocalDate.now().minusDays(2));
        antigua.setActivo(false);
        antigua.setArchivado(true);
        when(sesiones.findById(9L)).thenReturn(Optional.of(antigua));

        var response = controller.restaurarSesion(9L);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(Boolean.TRUE.equals(antigua.getArchivado()));
        verify(sesiones, never()).save(any());
    }

    private AsistenciaSesion sesion(Long id, LocalDate fecha) {
        AsistenciaSesion sesion = new AsistenciaSesion();
        sesion.setId(id);
        sesion.setCodigo("SESION-" + id);
        sesion.setNombre("Sesión " + id);
        sesion.setFecha(fecha);
        sesion.setTipo("reunion");
        sesion.setActivo(true);
        sesion.setArchivado(false);
        sesion.setCreadoEn(LocalDateTime.now());
        return sesion;
    }
}
