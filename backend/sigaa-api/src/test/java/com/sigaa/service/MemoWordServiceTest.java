package com.sigaa.service;

import com.sigaa.model.Memo;
import com.sigaa.model.MemoNumero;
import com.sigaa.model.Usuario;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class MemoWordServiceTest {
    private final MemoWordService service = new MemoWordService(new NotaWordService());

    @Test void generaDatosDelMemoSinInventarUnFirmanteNiAlterarSeguimiento() throws Exception {
        Memo memo = ejemplo();
        String xml = NotaWordServiceTest.entrada(service.generar(memo), "word/document.xml");
        assertThat(xml).contains("MEMORANDO N.° M-001-2026", "Área de prueba", "Emisor Ficticio", "Asunto de prueba", "Contenido de prueba")
            .doesNotContain("{{", "Seguimiento Ficticio");
        assertThat(memo.getSeguimientoUsuario().getNombre()).isEqualTo("Seguimiento");
        assertThat(memo.getEstado()).isEqualTo("EN_SEGUIMIENTO");
    }

    @Test void ambasPlantillasCompartenMembreteCompletoCartaYEspacioProtegido() throws Exception {
        byte[] nota = new NotaWordService().generar(NotaWordServiceTest.notaEjemplo());
        byte[] memo = service.generar(ejemplo());
        assertThat(java.util.Arrays.equals(entradaBinaria(memo, "word/media/image1.png"), entradaBinaria(nota, "word/media/image1.png")))
            .as("Notas y Memos utilizan el mismo membrete").isTrue();
        for (byte[] documento : new byte[][] {nota, memo}) {
            assertThat(NotaWordServiceTest.entrada(documento, "word/header1.xml"))
                .contains("MembreteInstitucional2026", "behindDoc=\"1\"", "relativeFrom=\"page\"");
            assertThat(NotaWordServiceTest.entrada(documento, "word/document.xml"))
                .contains("w:w=\"12240\"", "w:h=\"15840\"", "w:top=\"2160\"", "w:bottom=\"2160\"");
        }
    }

    @Test void conservaVistaPreviaAnuladaPeroRechazaDocumentoDefinitivo() {
        Memo memo = ejemplo(); memo.setEstado("ANULADO");
        assertThat(service.generarVistaPrevia(memo)).isNotEmpty();
        assertThatThrownBy(() -> service.generar(memo)).hasMessageContaining("anulado");
    }

    @Test void nombreDeDescargaNoPermiteRutasNiCaracteresDeControl() {
        Memo memo = ejemplo(); memo.setAsunto("Asunto / coordinación: <prueba>");
        assertThat(MemoWordService.nombreArchivo(memo, "pdf")).isEqualTo("MEMO-M-001-2026-ASUNTO-COORDINACION-PRUEBA.PDF");
    }

    static Memo ejemplo() {
        MemoNumero numero = new MemoNumero(); numero.setNumero("M-001-2026"); numero.setEstado("OCUPADO");
        Usuario emisor = new Usuario(); emisor.setNombre("Emisor"); emisor.setApellido("Ficticio");
        Usuario seguimiento = new Usuario(); seguimiento.setNombre("Seguimiento"); seguimiento.setApellido("Ficticio");
        Memo memo = new Memo(); memo.setNumero(numero); memo.setAsunto("Asunto de prueba"); memo.setDestino("Área de prueba");
        memo.setDescripcion("Contenido de prueba"); memo.setCreadoPor(emisor); memo.setSeguimientoUsuario(seguimiento);
        ReflectionTestUtils.setField(memo, "createdAt", LocalDateTime.of(2026, 10, 19, 9, 0)); return memo;
    }

    private byte[] entradaBinaria(byte[] docx, String nombre) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) if (entry.getName().equals(nombre)) return zip.readAllBytes();
        }
        throw new AssertionError("No se encontró el membrete en el documento.");
    }
}
