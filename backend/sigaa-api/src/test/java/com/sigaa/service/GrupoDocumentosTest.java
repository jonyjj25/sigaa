package com.sigaa.service;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GrupoDocumentosTest {
    private final GrupoRepository grupos = mock(GrupoRepository.class);
    private final DocumentoRepository documentos = mock(DocumentoRepository.class);
    private final DocumentoAccessService access = mock(DocumentoAccessService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final Usuario usuario = new Usuario();
    private GrupoService service;
    private Documento visible;
    private Documento privado;

    @BeforeEach
    void preparar() {
        usuario.setId(1L);
        usuario.setCorreo("usuario@example.test");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(usuario.getCorreo(), "", List.of()));
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        Unidad unidad = new Unidad(); unidad.setNombre("Unidad de prueba");
        Grupo grupo = new Grupo(); grupo.setUnidad(unidad);
        GrupoUsuario miembro = new GrupoUsuario(); miembro.setUsuario(usuario); miembro.setActivo(true);
        grupo.getMiembros().add(miembro);
        when(grupos.findById(7L)).thenReturn(Optional.of(grupo));
        visible = documento(1L, "Visible"); privado = documento(2L, "Confidencial");
        when(documentos.findByAreaIgnoreCaseOrderByFechaDesc("Unidad de prueba")).thenReturn(List.of(visible, privado));
        when(access.evaluarListado(List.of(visible, privado), usuario)).thenReturn(Map.of(
            1L, new DocumentoAccessService.Acceso(false, false, false),
            2L, new DocumentoAccessService.Acceso(false, false, false)));
        service = new GrupoService(grupos, mock(GrupoUsuarioRepository.class), mock(UnidadRepository.class),
            usuarios, mock(PermissionRepository.class), mock(AuditLogRepository.class), mock(GrupoMensajeRepository.class),
            mock(InternalNotificationService.class), mock(UsuarioUnidadRepository.class), mock(TareaRepository.class),
            documentos, access);
    }

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void listadoYTotalesAplicanLaMismaPoliticaDeDocumentos() {
        when(access.evaluarListado(List.of(visible, privado), usuario)).thenReturn(Map.of(
            1L, new DocumentoAccessService.Acceso(true, false, false),
            2L, new DocumentoAccessService.Acceso(false, false, false)));
        assertThat(service.archivos(7L)).hasSize(1).allSatisfy(item -> assertThat(item.get("id")).isEqualTo(1L));
        assertThat(service.resumen(7L).get("archivos")).isEqualTo(1L);
        verify(access, times(2)).evaluarListado(List.of(visible, privado), usuario);
        verify(documentos, never()).findAllByOrderByFechaDesc();
        verify(access, never()).canView(any(), any());
    }

    @Test
    void noExponeMetadatosNiTotalesCuandoNingunDocumentoEsVisible() {
        assertThat(service.archivos(7L)).isEmpty();
        assertThat(service.resumen(7L).get("archivos")).isEqualTo(0L);
    }

    private Documento documento(long id, String nombre) {
        Documento documento = new Documento(); documento.setId(id); documento.setNombre(nombre);
        documento.setArea("Unidad de prueba"); return documento;
    }
}
