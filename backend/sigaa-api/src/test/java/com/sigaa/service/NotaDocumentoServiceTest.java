package com.sigaa.service;

import com.sigaa.model.Nota;
import com.sigaa.model.NotaPdf;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaPdfRepository;
import com.sigaa.repository.NotaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotaDocumentoServiceTest {
    private NotaRepository notas;
    private NotaPdfRepository pdfs;
    private NotaPdfStorageService storage;
    private NotaTrazabilidadService trazabilidad;
    private NotaDocumentoService service;
    private Nota nota;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        notas = mock(NotaRepository.class); pdfs = mock(NotaPdfRepository.class);
        storage = mock(NotaPdfStorageService.class); trazabilidad = mock(NotaTrazabilidadService.class);
        service = new NotaDocumentoService(notas, pdfs, storage, trazabilidad);
        nota = new Nota(); nota.setId(5L); nota.setEstado("CERRADA"); nota.setAsunto("Intacto");
        usuario = new Usuario(); usuario.setId(2L);
        when(notas.findByIdForUpdate(5L)).thenReturn(Optional.of(nota));
        when(trazabilidad.firmaReceptorRegistrada(nota)).thenReturn(true);
        when(storage.guardar(any())).thenReturn(new NotaPdfStorageService.StoredPdf("final.pdf", "safe/path.pdf", "application/pdf", 20L));
        when(pdfs.save(any())).thenAnswer(invocation -> { NotaPdf pdf = invocation.getArgument(0); pdf.setId(9L); return pdf; });
    }

    @Test
    void adjuntaPdfEnNotaCerradaSinModificarSusDatos() {
        when(pdfs.maxVersion(5L)).thenReturn(0);
        NotaPdf pdf = service.adjuntar(5L, archivo(), usuario);

        assertThat(pdf.getNumeroVersion()).isEqualTo(1);
        assertThat(nota.getEstado()).isEqualTo("CERRADA");
        assertThat(nota.getAsunto()).isEqualTo("Intacto");
        assertThat(nota.getPdfNombre()).isEqualTo("final.pdf");
        verify(trazabilidad).historial(eq(nota), eq(usuario), eq("ADJUNTAR_PDF"), eq("CERRADA"), eq("CERRADA"), contains("v1"));
    }

    @Test
    void nuevaCargaIncrementaVersionYSoloDesmarcaLaAnterior() {
        when(pdfs.maxVersion(5L)).thenReturn(2);
        NotaPdf pdf = service.adjuntar(5L, archivo(), usuario);

        assertThat(pdf.getNumeroVersion()).isEqualTo(3);
        verify(pdfs).desmarcarVigente(5L);
        verify(trazabilidad).historial(eq(nota), eq(usuario), eq("REEMPLAZAR_PDF"), any(), any(), contains("v3"));
    }

    @Test
    void propagaElRechazoDeUnArchivoQueNoEsPdf() {
        when(storage.guardar(any())).thenThrow(new IllegalArgumentException("PDF inválido"));
        assertThatThrownBy(() -> service.adjuntar(5L, archivo(), usuario))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PDF");
        verify(trazabilidad, never()).historial(any(), any(), any(), any(), any(), any());
        verify(trazabilidad, never()).audit(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BORRADOR", "EN_SEGUIMIENTO", "ANULADA"})
    void rechazaCargaFueraDelEstadoPermitidoSinGuardarArchivo(String estado) {
        nota.setEstado(estado);
        assertThatThrownBy(() -> service.adjuntar(5L, archivo(), usuario))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("firma del receptor");
        verifyNoInteractions(storage, pdfs);
        verify(notas, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIRMADA", "CERRADA"})
    void firmaDeDireccionOCierreNoSustituyenLaFirmaDelReceptor(String estado) {
        nota.setEstado(estado); when(trazabilidad.firmaReceptorRegistrada(nota)).thenReturn(false);
        assertThatThrownBy(() -> service.adjuntar(5L, archivo(), usuario))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("firma del receptor");
        verifyNoInteractions(storage, pdfs);
        verify(notas, never()).save(any());
    }

    @Test
    void adjuntaDespuesDeFirmaDelReceptorMientrasLaNotaSigueAbierta() {
        nota.setEstado("FIRMADA");
        assertThat(service.adjuntar(5L, archivo(), usuario).getNumeroVersion()).isEqualTo(1);
        assertThat(nota.getEstado()).isEqualTo("FIRMADA");
        verify(storage).guardar(any());
    }

    @Test
    void eliminaLaVersionVigenteConMotivoYRestauraLaAnterior() {
        NotaPdf vigente = new NotaPdf(); vigente.setId(9L); vigente.setNota(nota); vigente.setNumeroVersion(2);
        vigente.setNombreOriginal("actual.pdf"); vigente.setRutaInterna("safe/actual.pdf"); vigente.setVigente(true);
        NotaPdf anterior = new NotaPdf(); anterior.setId(8L); anterior.setNota(nota); anterior.setNumeroVersion(1);
        anterior.setNombreOriginal("anterior.pdf"); anterior.setRutaInterna("safe/anterior.pdf"); anterior.setTamanoBytes(15L); anterior.setVigente(false);
        when(pdfs.findByIdAndNotaIdAndEliminadoAtIsNull(9L, 5L)).thenReturn(Optional.of(vigente));
        when(pdfs.findFirstByNotaIdAndEliminadoAtIsNullOrderByNumeroVersionDesc(5L)).thenReturn(Optional.of(anterior));

        service.eliminar(5L, 9L, "Se cargó el documento equivocado", usuario);

        assertThat(vigente.getEliminadoPor()).isSameAs(usuario);
        assertThat(vigente.getEliminacionMotivo()).isEqualTo("Se cargó el documento equivocado");
        assertThat(vigente.getVigente()).isFalse();
        assertThat(anterior.getVigente()).isTrue();
        assertThat(nota.getPdfNombre()).isEqualTo("anterior.pdf");
        verify(storage).eliminarTrasConfirmar("safe/actual.pdf");
        verify(trazabilidad).historial(eq(nota), eq(usuario), eq("ELIMINAR_PDF"), any(), any(), contains("documento equivocado"));
    }

    @Test
    void exigeUnMotivoValidoAntesDeEliminar() {
        NotaPdf vigente = new NotaPdf(); vigente.setId(9L); vigente.setNota(nota); vigente.setVigente(true);
        when(pdfs.findByIdAndNotaIdAndEliminadoAtIsNull(9L, 5L)).thenReturn(Optional.of(vigente));

        assertThatThrownBy(() -> service.eliminar(5L, 9L, "   ", usuario))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");

        verify(pdfs, never()).save(any());
        verifyNoInteractions(trazabilidad);
        verify(storage, never()).eliminarTrasConfirmar(anyString());
    }

    @Test
    void eliminaElUnicoPdfYDejaLaNotaPendienteDeDocumento() {
        NotaPdf vigente = new NotaPdf(); vigente.setId(9L); vigente.setNota(nota); vigente.setNumeroVersion(1);
        vigente.setNombreOriginal("unico.pdf"); vigente.setRutaInterna("safe/unico.pdf"); vigente.setVigente(true);
        nota.setPdfNombre("unico.pdf"); nota.setPdfRuta("safe/unico.pdf"); nota.setPdfTamanoBytes(20L);
        when(pdfs.findByIdAndNotaIdAndEliminadoAtIsNull(9L, 5L)).thenReturn(Optional.of(vigente));
        when(pdfs.findFirstByNotaIdAndEliminadoAtIsNullOrderByNumeroVersionDesc(5L)).thenReturn(Optional.empty());

        service.eliminar(5L, 9L, "El documento ya no corresponde", usuario);

        assertThat(nota.getPdfNombre()).isNull();
        assertThat(nota.getPdfRuta()).isNull();
        assertThat(nota.getPdfTamanoBytes()).isNull();
        verify(notas).save(nota);
        verify(storage).eliminarTrasConfirmar("safe/unico.pdf");
    }

    private MockMultipartFile archivo() {
        return new MockMultipartFile("pdf", "final.pdf", "application/pdf", "%PDF-1.7".getBytes());
    }
}
