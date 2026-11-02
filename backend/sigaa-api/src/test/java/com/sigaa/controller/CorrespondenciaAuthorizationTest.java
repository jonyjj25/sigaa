package com.sigaa.controller;

import com.sigaa.service.*;
import com.sigaa.repository.*;
import com.sigaa.model.*;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(CorrespondenciaAuthorizationTest.Config.class)
class CorrespondenciaAuthorizationTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean NotaUsuarioActualService session() { return mock(NotaUsuarioActualService.class); }
        @Bean NotaRepository notas() { return mock(NotaRepository.class); }
        @Bean MemoRepository memos() { return mock(MemoRepository.class); }
        @Bean CorrespondenciaAccessService correspondenciaAccessService() { return new CorrespondenciaAccessService(session(), notas(), memos()); }
        @Bean NotaDocumentoService documentos() { return mock(NotaDocumentoService.class); }
        @Bean NotaService servicioNotas() { return mock(NotaService.class); }
        @Bean MemoService servicioMemos() { return mock(MemoService.class); }
        @Bean MemoController memoController() {
            return new MemoController(servicioMemos(), mock(MemoNumeroService.class), mock(MemoNumeroRepository.class),
                mock(MemoHistorialRepository.class), session(), mock(MemoTrazabilidadService.class), correspondenciaAccessService(),
                mock(MemoWordService.class), mock(NotaRenderService.class));
        }
        @Bean NotaController controller() {
            return new NotaController(servicioNotas(), mock(NotaNumeroService.class), documentos(),
                mock(NotaRelacionService.class), session(),
                mock(NotaHistorialRepository.class), mock(NotaTrazabilidadService.class),
                mock(OrganizationRepository.class), mock(UsuarioRepository.class), mock(NotaWordService.class), correspondenciaAccessService(), mock(NotaRenderService.class));
        }
    }
    @Autowired NotaController controller;
    @Autowired NotaRepository notas;
    @Autowired NotaUsuarioActualService session;
    @Autowired NotaDocumentoService documentos;
    @Autowired NotaService servicioNotas;
    @Autowired MemoService servicioMemos;
    @Autowired MemoController memoController;
    @Autowired MemoRepository memos;

    @BeforeEach void preparar() {
        reset(notas, session, documentos, servicioNotas, servicioMemos, memos);
        Usuario creador = new Usuario(); creador.setId(1L);
        Nota nota = new Nota(); nota.setCreadoPor(creador);
        when(notas.findById(10L)).thenReturn(Optional.of(nota));
        Usuario ajeno = new Usuario(); ajeno.setId(2L); when(session.actual()).thenReturn(ajeno);
    }

    @Test @WithMockUser(authorities = {"Notas:Ver", "Notas:Descargar"})
    void permisosGeneralesNoPermitenDetalleNiDescargasDeOtraNota() {
        assertThatThrownBy(() -> controller.obtener(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.descargarPdf(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.descargarVersion(10L, 20L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.descargarWord(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.descargarDocumentoPdf(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.vistaPrevia(new com.sigaa.dto.NotaVistaPreviaRequest(
            10L, null, null, null, null, null, null, null)))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(documentos);
    }

    @Test @WithMockUser(authorities = "Notas:Descargar")
    void participantePuedeConsultarVersionesConPermisoFuncional() {
        Usuario creador = new Usuario(); creador.setId(1L); when(session.actual()).thenReturn(creador);
        when(documentos.listar(10L)).thenReturn(List.of());
        assertThat(controller.listarPdfs(10L)).isEmpty(); verify(documentos).listar(10L);
    }

    @Test @WithMockUser(authorities = "Notas:Ver")
    void participanteSinPermisoDeDescargaSigueDenegado() {
        Usuario creador = new Usuario(); creador.setId(1L); when(session.actual()).thenReturn(creador);
        assertThatThrownBy(() -> controller.listarPdfs(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(documentos);
    }

    @Test @WithMockUser(authorities = {"Notas:Ver", "Memos:Ver"})
    void listadosOmitenCorrespondenciaAjena() {
        Nota ajena = notas.findById(10L).orElseThrow();
        when(servicioNotas.listar()).thenReturn(List.of(ajena));
        Memo ajeno = new Memo(); ajeno.setCreadoPor(ajena.getCreadoPor());
        when(servicioMemos.listar()).thenReturn(List.of(ajeno));
        assertThat(controller.listar()).isEmpty();
        assertThat(memoController.listar()).isEmpty();
    }

    @Test @WithMockUser(authorities = {"Memos:Ver", "Memos:Completar"})
    void memoAjenoDeniegaDetalleYAcciones() {
        Usuario creador = new Usuario(); creador.setId(1L);
        Memo ajeno = new Memo(); ajeno.setCreadoPor(creador);
        when(memos.findById(10L)).thenReturn(Optional.of(ajeno));
        assertThatThrownBy(() -> memoController.obtener(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> memoController.completar(10L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(servicioMemos);
    }

    @Test @WithMockUser(authorities = "Notas:Descargar")
    void superadminConPermisoPuedeConsultarVersionesAjenas() {
        Usuario admin = new Usuario(); admin.setId(2L); admin.setRoles(java.util.Set.of(new Role("superadmin", "")));
        when(session.actual()).thenReturn(admin); when(documentos.listar(10L)).thenReturn(List.of());
        assertThat(controller.listarPdfs(10L)).isEmpty(); verify(documentos).listar(10L);
    }

    @Test @WithMockUser(authorities = "Notas:Descargar")
    void administradorRetiradoNoPuedeConsultarVersionesAjenas() {
        Usuario retirado = new Usuario(); retirado.setId(2L);
        retirado.setRoles(java.util.Set.of(new Role("administrador", "")));
        when(session.actual()).thenReturn(retirado);
        assertThatThrownBy(() -> controller.listarPdfs(10L))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(documentos);
    }

    @Test @WithMockUser(authorities = "Notas:Ver")
    void busquedaPaginadaUsaIdentidadDeSesionSinAccesoGlobal() {
        when(servicioNotas.buscarAutorizadas("prueba", null, 0, 20, 2L, false))
            .thenReturn(org.springframework.data.domain.Page.empty());
        assertThat(controller.buscar("prueba", null, 0, 20).get("totalElements")).isEqualTo(0L);
        verify(servicioNotas).buscarAutorizadas("prueba", null, 0, 20, 2L, false);
    }

    @Test @WithMockUser(authorities = {"Notas:Ver", "Notas:Dar seguimiento", "Notas:Cambiar seguimiento", "Notas:Firmar"})
    void seguimientoAjenoSeDeniegaAunqueExistanTodosLosPermisosFuncionales() {
        assertThatThrownBy(() -> controller.seguimiento(10L, new com.sigaa.dto.NotaSeguimientoRequest("RECIBIDA", "Comentario")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(servicioNotas);
    }

    @Test @WithMockUser(authorities = "Notas:Ver")
    void lecturaNoAutorizaAgregarSeguimientoNiCambiarEstados() {
        Usuario creador = new Usuario(); creador.setId(1L); when(session.actual()).thenReturn(creador);
        assertThatThrownBy(() -> controller.seguimiento(10L, new com.sigaa.dto.NotaSeguimientoRequest(null, "Comentario")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(servicioNotas);
    }

    @Test @WithMockUser(authorities = "Notas:Editar")
    void editarBorradorNoPermiteEvitarElPermisoDeComentarios() {
        Usuario creador = new Usuario(); creador.setId(1L); when(session.actual()).thenReturn(creador);
        var request = new com.sigaa.dto.NotaUpdateRequest(); request.setDescripcion("Comentario mediante edición");
        assertThatThrownBy(() -> controller.actualizar(10L, request, null))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(servicioNotas);
    }

    @Test @WithMockUser(authorities = {"Notas:Ver", "Notas:Dar seguimiento"})
    void comentarNoEntregaPermisosParaCambiarEstadoNiRegistrarFirmaDelReceptor() {
        Usuario creador = new Usuario(); creador.setId(1L); when(session.actual()).thenReturn(creador);
        when(session.tieneAutoridad("Notas:Dar seguimiento")).thenReturn(true);
        var request = new com.sigaa.dto.NotaSeguimientoRequest("FIRMADA_RECEPTOR", "Comentario");
        when(servicioNotas.seguimiento(eq(10L), same(request), same(creador), eq(true), eq(java.util.Set.of())))
            .thenThrow(new org.springframework.security.access.AccessDeniedException("Firma denegada"));
        assertThatThrownBy(() -> controller.seguimiento(10L, request)).hasMessageContaining("Firma denegada");
        verify(servicioNotas).seguimiento(10L, request, creador, true, java.util.Set.of());
    }

    @Test @WithMockUser(authorities = {"Notas:Ver", "Notas:Cambiar seguimiento"})
    void cambiarSeguimientoNoEntregaPermisoDeFirmaNiComentarios() {
        Usuario creador = new Usuario(); creador.setId(1L); when(session.actual()).thenReturn(creador);
        when(session.tieneAutoridad("Notas:Cambiar seguimiento")).thenReturn(true);
        var request = new com.sigaa.dto.NotaSeguimientoRequest("FIRMADA_RECEPTOR", "");
        var permitidos = java.util.EnumSet.allOf(NotaSeguimientoEstado.class); permitidos.remove(NotaSeguimientoEstado.FIRMADA_RECEPTOR);
        when(servicioNotas.seguimiento(eq(10L), same(request), same(creador), eq(false), eq(permitidos)))
            .thenThrow(new org.springframework.security.access.AccessDeniedException("Firma denegada"));
        assertThatThrownBy(() -> controller.seguimiento(10L, request)).hasMessageContaining("Firma denegada");
        verify(servicioNotas).seguimiento(10L, request, creador, false, permitidos);
    }
}
