package com.sigaa.controller;

import com.sigaa.model.Documento;
import com.sigaa.model.DocumentoUsuarioAcceso;
import com.sigaa.model.Tarea;
import com.sigaa.model.TareaAdjunto;
import com.sigaa.model.Usuario;
import com.sigaa.repository.*;
import com.sigaa.service.DocumentoAccessService;
import com.sigaa.service.InternalNotificationService;
import com.sigaa.service.TareaAccessService;
import com.sigaa.util.UploadStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentoArchivosTest {
    @TempDir Path directory;
    private final DocumentoRepository documentos = mock(DocumentoRepository.class);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final DocumentoVersionRepository versiones = mock(DocumentoVersionRepository.class);
    private final Map<Long, com.sigaa.model.DocumentoVersion> registradas = new LinkedHashMap<>();
    private final CarpetaRepository carpetas = mock(CarpetaRepository.class);
    private final CarpetaUsuarioRepository carpetaUsuarios = mock(CarpetaUsuarioRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final DocumentoUsuarioAccesoRepository accesos = mock(DocumentoUsuarioAccesoRepository.class);
    private final DocumentoAccessService access = mock(DocumentoAccessService.class);
    private final InternalNotificationService notifications = mock(InternalNotificationService.class);
    private final TareaAccessService tareas = mock(TareaAccessService.class);
    private final TareaAdjuntoRepository tareaAdjuntos = mock(TareaAdjuntoRepository.class);
    private DocumentoController controller;
    private Path original;
    private Documento documento;
    private Usuario usuario;

    @BeforeEach void preparar() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        original = Files.writeString(directory.resolve("original.txt"), "original");
        documento = new Documento(); documento.setId(1L); documento.setNombre("Prueba");
        documento.setFecha(LocalDate.of(2099, 1, 1));
        documento.setArchivoRuta(original.toString());
        when(documentos.findByIdForUpdate(1L)).thenReturn(Optional.of(documento));
        usuario = new Usuario(); usuario.setId(1L); usuario.setCorreo("usuario@example.test");
        when(usuarios.findByCorreo(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getCorreo(), "", List.of()));
        when(access.canModify(documento, usuario)).thenReturn(true);
        when(access.canManageAccess(documento, usuario)).thenReturn(true);
        when(access.canDelete(documento, usuario)).thenReturn(true);
        when(tareas.tareasVisiblesActual()).thenReturn(List.of());
        controller = new DocumentoController(documentos, carpetas, carpetaUsuarios,
            versiones, audit, usuarios, mock(OrgUnitRepository.class),
            mock(DocumentoSolicitudAprobacionRepository.class), accesos,
            access, notifications, mock(TipoDocumentoRepository.class), tareas, tareaAdjuntos);
        when(versiones.saveAndFlush(any())).thenAnswer(invocation -> {
            com.sigaa.model.DocumentoVersion version = invocation.getArgument(0);
            if (version.getId() == null) version.setId((long) registradas.size() + 1);
            registradas.put(version.getId(), version); return version;
        });
        when(versiones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(versiones.findByIdAndDocumentoId(anyLong(), anyLong())).thenAnswer(invocation -> Optional.ofNullable(registradas.get(invocation.getArgument(0))));
        when(versiones.ultimoNumero(anyLong())).thenAnswer(invocation -> registradas.values().stream().mapToInt(v -> v.getNumero()).max().orElse(0));
        ReflectionTestUtils.setField(controller, "uploadDir", directory);
    }

    @AfterEach void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) completar(false);
        SecurityContextHolder.clearContext();
    }

    @Test void reemplazoConservaOriginalTambienTrasCommit() throws IOException {
        var respuesta = controller.subirArchivo(1L, archivo());
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).containsEntry("archivoRuta", "/api/documentos/1/descargar");
        Path nuevo = Path.of(documento.getArchivoRuta());
        assertThat(original).exists(); assertThat(nuevo).exists();
        completar(true);
        assertThat(original).exists(); assertThat(Files.readString(nuevo)).isEqualTo("nuevo");
    }

    @Test void falloDeBaseDeDatosLimpiaNuevoYConservaOriginal() throws IOException {
        when(documentos.save(documento)).thenAnswer(invocation -> {
            if (!original.toString().equals(documento.getArchivoRuta())) throw new IllegalStateException("fallo simulado");
            return documento;
        });
        assertThatThrownBy(() -> controller.subirArchivo(1L, archivo())).isInstanceOf(IllegalStateException.class);
        Path nuevo = Path.of(documento.getArchivoRuta());
        completar(false);
        assertThat(original).exists(); assertThat(nuevo).doesNotExist();
        assertThat(Files.readString(original)).isEqualTo("original");
    }

    @Test void retiradaDelDocumentoConservaElArchivoHistorico() {
        controller.eliminarDocumento(1L);
        assertThat(original).exists();
        completar(true);
        assertThat(original).exists();
    }

    @Test void falloDeAuditoriaAlReemplazarRevierteLaLimpieza() {
        when(audit.save(any())).thenAnswer(invocation -> {
            com.sigaa.model.AuditLog registro = invocation.getArgument(0);
            if ("ACTUALIZAR_ARCHIVO".equals(registro.getAccion())) throw new IllegalStateException("fallo simulado");
            return registro;
        });
        assertThatThrownBy(() -> controller.subirArchivo(1L, archivo())).isInstanceOf(IllegalStateException.class);
        Path nuevo = Path.of(documento.getArchivoRuta());
        completar(false);
        assertThat(original).exists(); assertThat(nuevo).doesNotExist();
    }

    @Test void contenidoFalsoNoReemplazaOriginal() throws IOException {
        var falso = new MockMultipartFile("file", "falso.pdf", "application/pdf", "no es un pdf".getBytes());
        assertThat(controller.subirArchivo(1L, falso).getStatusCode().value()).isEqualTo(400);
        assertThat(documento.getArchivoRuta()).isEqualTo(original.toString());
        verify(documentos, never()).save(any());
        try (var paths = Files.list(directory)) { assertThat(paths.toList()).containsExactly(original); }
    }

    @Test void listarNoLeePdfNiGuardaElConteoDePaginas() throws IOException {
        Path archivoPdf = directory.resolve("prueba.pdf");
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage()); pdf.save(archivoPdf.toFile());
        }
        documento.setArchivoRuta(archivoPdf.toString()); documento.setPaginas(0); documento.setFecha(java.time.LocalDate.now());
        when(documentos.findAllByOrderByFechaDesc()).thenReturn(List.of(documento));
        var access = (DocumentoAccessService) ReflectionTestUtils.getField(controller, "documentoAccessService");
        when(access.evaluarListado(anyList(), any())).thenReturn(java.util.Map.of(1L, new DocumentoAccessService.Acceso(true, true, true)));
        assertThat(controller.listarDocumentos()).hasSize(1);
        assertThat(documento.getPaginas()).isZero(); verify(documentos, never()).save(any());
    }

    @Test void listarIncluyeSoloAdjuntosDocumentalesDeTareasVisibles() {
        when(documentos.findAllByOrderByFechaDesc()).thenReturn(List.of());
        Tarea tarea = new Tarea(); tarea.setId(44L); tarea.setCodigo("TAR-2026-44");
        tarea.setTitulo("Preparar informe"); tarea.setCreadoPor("Dueña");
        tarea.setUnidades("Planificación"); tarea.setFechaCreacion(java.time.LocalDate.of(2026, 10, 20));
        when(tareas.tareasVisiblesActual()).thenReturn(List.of(tarea));

        TareaAdjunto documentoTarea = adjunto(7L, 44L, "informe.docx", "guardado/informe.docx");
        TareaAdjunto imagen = adjunto(8L, 44L, "captura.png", "guardado/captura.png");
        when(tareaAdjuntos.findByTareaIdInOrderById(java.util.Set.of(44L)))
            .thenReturn(List.of(documentoTarea, imagen));

        assertThat(controller.listarDocumentos()).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("id", "tarea-7").containsEntry("origen", "TAREA")
                .containsEntry("categoria", "Tarea").containsEntry("area", "Planificación")
                .containsEntry("descargaUrl", "/api/tareas/44/adjuntos/7/descargar")
                .containsEntry("puedeEliminar", false);
        });
    }

    @Test void eliminarDocumentoNotificaAQuienConservabaUnAccesoCompartido() {
        documento.setPropietarioUsuarioId(usuario.getId());
        documento.setPropietario("Usuario Propietario");
        Usuario destinatario = new Usuario(); destinatario.setId(2L); destinatario.setCorreo("destino@example.test");
        DocumentoUsuarioAcceso compartido = new DocumentoUsuarioAcceso();
        compartido.setDocumento(documento); compartido.setUsuario(destinatario);
        when(accesos.findByDocumentoIdOrderByCreadoEnAsc(1L)).thenReturn(List.of(compartido));

        assertThat(controller.eliminarDocumento(1L).getStatusCode().value()).isEqualTo(204);

        verify(notifications).send(eq("documento_eliminado"), same(destinatario),
            eq("Documento eliminado"), contains("Usuario Propietario"));
    }

    @Test void falloDeAuditoriaAlEliminarConservaElArchivo() {
        when(audit.save(any())).thenThrow(new IllegalStateException("fallo simulado"));
        assertThatThrownBy(() -> controller.eliminarDocumento(1L)).isInstanceOf(IllegalStateException.class);
        completar(false);
        assertThat(original).exists();
    }

    @Test void copiaIncompletaNoDejaUnArchivoHuerfano() throws IOException {
        var file = new MockMultipartFile("file", "parcial.txt", "text/plain", new byte[0]) {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    private int bytes;
                    @Override public int read() throws IOException {
                        if (++bytes > 3) throw new IOException("fallo simulado");
                        return 'a';
                    }
                };
            }
        };
        assertThatThrownBy(() -> UploadStorage.store(directory, file)).isInstanceOf(IOException.class);
        try (var paths = Files.list(directory)) { assertThat(paths.toList()).containsExactly(original); }
    }

    private MockMultipartFile archivo() { return new MockMultipartFile("file", "nuevo.txt", "text/plain", "nuevo".getBytes()); }

    private TareaAdjunto adjunto(Long id, Long tareaId, String nombre, String ruta) {
        TareaAdjunto adjunto = new TareaAdjunto(); adjunto.setId(id); adjunto.setTareaId(tareaId);
        adjunto.setNombre(nombre); adjunto.setRutaArchivo(ruta); adjunto.setTamano("2 KB");
        return adjunto;
    }

    private void completar(boolean commit) {
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        if (commit) callbacks.forEach(TransactionSynchronization::afterCommit);
        callbacks.forEach(callback -> callback.afterCompletion(commit ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
