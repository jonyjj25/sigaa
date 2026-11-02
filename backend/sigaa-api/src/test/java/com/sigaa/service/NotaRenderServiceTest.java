package com.sigaa.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

class NotaRenderServiceTest {
    @Test
    void generaPdfIntegradoCuandoLibreOfficeNoEstaConfigurado() throws Exception {
        var nota = NotaWordServiceTest.notaEjemplo();
        byte[] pdf = new NotaRenderService("").pdf(new NotaWordService().generarVistaPrevia(nota));
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                .contains("SIGAA", "Nota. N° OAT-015-2026", "JUAN PÉREZ", "ANA MARÍA DE LA CRUZ");
        }
    }

    @Test
    void usaPdfIntegradoCuandoLaRutaDeLibreOfficeNoExiste() throws Exception {
        var nota = NotaWordServiceTest.notaEjemplo();
        byte[] word = new NotaWordService().generarVistaPrevia(nota);
        var preview = new NotaRenderService("Z:/sigaa/no-existe/soffice.exe")
            .vistaPrevia(word, NotaWordService.nombreArchivo(nota, "docx"));
        assertThat(preview.totalPaginas()).isGreaterThanOrEqualTo(1);
        assertThat(preview.paginas()).allMatch(page -> page.startsWith("data:image/png;base64,"));
    }

    @Test
    void pdfIntegradoRespetaSaltosDePaginaExplicitos() throws Exception {
        var nota = NotaWordServiceTest.notaEjemplo();
        nota.setContenido("Contenido de la primera página.\n\f\nContenido de la segunda página.");
        byte[] pdf = new NotaRenderService("").pdf(new NotaWordService().generarVistaPrevia(nota));
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            var first = new PDFTextStripper(); first.setStartPage(1); first.setEndPage(1);
            var second = new PDFTextStripper(); second.setStartPage(2); second.setEndPage(2);
            assertThat(first.getText(document)).contains("Contenido de la primera página.");
            assertThat(second.getText(document)).contains("Contenido de la segunda página.");
        }
    }

    @Test
    void reutilizaConversionYPaginasAunqueCambienFechasYOrdenDelZip() throws Exception {
        var renderer = new FakeRenderer(pdfEjemplo(2));
        var first = renderer.vistaPrevia(wordEjemplo("Texto A", 1000, false), "NOTA-A.DOCX");
        var reopened = renderer.vistaPrevia(wordEjemplo("Texto A", 20000, true), "OTRO-NOMBRE.DOCX");
        assertThat(renderer.conversions.get()).isEqualTo(1);
        assertThat(reopened.paginas()).isSameAs(first.paginas());
        assertThat(reopened.nombreArchivo()).isEqualTo("OTRO-NOMBRE.DOCX");
        assertThat(renderer.pdf(wordEjemplo("Texto A", 30000, false))).containsExactly(renderer.output);
        assertThat(renderer.conversions.get()).isEqualTo(1);
        renderer.vistaPrevia(wordEjemplo("Texto B", 1000, false), "NOTA-B.DOCX");
        assertThat(renderer.conversions.get()).isEqualTo(2);
    }

    @Test
    void deduplicaConversionesConcurrentesDelMismoDocumento() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var renderer = new FakeRenderer(pdfEjemplo(1)) {
            @Override protected byte[] convertirPdf(byte[] word) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Prueba sin liberar"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return super.convertirPdf(word);
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> renderer.vistaPrevia(wordEjemplo("Igual", 1000, false), "NOTA.DOCX"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> renderer.vistaPrevia(wordEjemplo("Igual", 20000, true), "NOTA.DOCX"));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).paginas()).isSameAs(second.get(5, TimeUnit.SECONDS).paginas());
            assertThat(renderer.conversions.get()).isEqualTo(1);
        } finally { release.countDown(); }
    }

    @Test
    void noCacheaConversionFallidaYPermiteRecuperacionPosterior() throws Exception {
        var renderer = new FakeRenderer(pdfEjemplo(1)) {
            private int attempt;
            @Override protected byte[] convertirPdf(byte[] word) {
                if (attempt++ == 0) throw new IllegalStateException("Fallo temporal de LibreOffice");
                if (attempt == 2) return new byte[0];
                return super.convertirPdf(word);
            }
        };
        byte[] word = wordEjemplo("Mismo contenido", 1000, false);
        assertThatThrownBy(() -> renderer.vistaPrevia(word, "NOTA.DOCX")).hasMessageContaining("Fallo temporal");
        assertThatThrownBy(() -> renderer.vistaPrevia(word, "NOTA.DOCX")).hasMessageContaining("PDF válido");
        assertThat(renderer.vistaPrevia(word, "NOTA.DOCX").totalPaginas()).isEqualTo(1);
        assertThat(renderer.conversions.get()).isEqualTo(1);
    }

    @Test
    void limitaElInventarioDeDocumentosEnMemoria() throws Exception {
        var renderer = new FakeRenderer(pdfEjemplo(1));
        for (int i = 0; i < 13; i++) renderer.pdf(wordEjemplo("Documento " + i, 1000, false));
        renderer.pdf(wordEjemplo("Documento 12", 1000, false));
        assertThat(renderer.conversions.get()).isEqualTo(13);
        renderer.pdf(wordEjemplo("Documento 0", 1000, false));
        assertThat(renderer.conversions.get()).isEqualTo(14);
    }

    @Test
    void identificaAnuladaEnTodasLasPaginasSinCambiarElDocumentoVigente() throws Exception {
        var renderer = new FakeRenderer(pdfEjemplo(3));
        byte[] word = wordEjemplo("Documento institucional", 1000, false);
        var vigente = renderer.vistaPrevia(word, "NOTA.DOCX");
        var anulada = renderer.vistaPrevia(word, "NOTA.DOCX", true);
        assertThat(anulada.paginas()).hasSize(3).isNotEqualTo(vigente.paginas());
        try (var original = Loader.loadPDF(renderer.pdf(word)); var marked = Loader.loadPDF(renderer.pdf(word, true))) {
            assertThat(new PDFTextStripper().getText(original)).doesNotContain("ANULADA");
            for (int i = 0; i < 3; i++) {
                var stripper = new PDFTextStripper(); stripper.setStartPage(i + 1); stripper.setEndPage(i + 1);
                assertThat(stripper.getText(marked)).contains("Contenido legible pagina " + (i + 1), "ANULADA");
                var actualPage = new ByteArrayOutputStream();
                ImageIO.write(new PDFRenderer(marked).renderImageWithDPI(i, 96), "png", actualPage);
                assertThat(Base64.getDecoder().decode(anulada.paginas().get(i).substring("data:image/png;base64,".length())))
                    .containsExactly(actualPage.toByteArray());
            }
        }
        assertThat(renderer.vistaPrevia(word, "NOTA.DOCX", true).paginas()).isSameAs(anulada.paginas());
        assertThat(renderer.vistaPrevia(word, "NOTA.DOCX").paginas()).isSameAs(vigente.paginas());
        assertThat(renderer.conversions.get()).isEqualTo(1);
    }

    @Test
    void distingueLaMarcaDelMemoSinCambiarLaNotaNiRepetirLaConversion() throws Exception {
        var renderer = new FakeRenderer(pdfEjemplo(1));
        byte[] word = wordEjemplo("Documento institucional", 1000, false);
        var memo = renderer.vistaPrevia(word, "MEMO.DOCX", "ANULADO");
        var nota = renderer.vistaPrevia(word, "NOTA.DOCX", true);
        assertThat(memo.paginas()).isNotEqualTo(nota.paginas());
        assertThat(renderer.vistaPrevia(word, "MEMO.DOCX", "ANULADO").paginas()).isSameAs(memo.paginas());
        try (var vigente = Loader.loadPDF(renderer.pdf(word))) {
            assertThat(new PDFTextStripper().getText(vigente)).doesNotContain("ANULADO", "ANULADA");
        }
        assertThat(renderer.conversions.get()).isEqualTo(1);
    }

    private static byte[] pdfEjemplo(int pages) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                var page = new PDPage(PDRectangle.LETTER); document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    stream.newLineAtOffset(72, 720); stream.showText("Contenido legible pagina " + (i + 1)); stream.endText();
                }
            }
            document.save(output); return output.toByteArray();
        }
    }

    private static byte[] wordEjemplo(String text, long time, boolean reverse) throws Exception {
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            for (String path : reverse ? List.of("word/styles.xml", "word/document.xml") : List.of("word/document.xml", "word/styles.xml")) {
                var entry = new ZipEntry(path); entry.setTime(time); zip.putNextEntry(entry);
                zip.write((path.equals("word/document.xml") ? text : "Verdana 11").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish(); return output.toByteArray();
        }
    }

    private static class FakeRenderer extends NotaRenderService {
        private final byte[] output;
        final AtomicInteger conversions = new AtomicInteger();
        FakeRenderer(byte[] output) { super("prueba-sin-procesos"); this.output = output; }
        @Override protected byte[] convertirPdf(byte[] word) { conversions.incrementAndGet(); return output; }
    }

    // La conversión real requiere el ejecutable existente, sin descargar herramientas durante las pruebas.
    @Test
    @EnabledIfSystemProperty(named = "sigaa.note.render.command", matches = ".+")
    void vistaPreviaCoincideConPdfEnCartaUnaPaginaVariasPaginasYSaltosExplicitos() throws Exception {
        var wordService = new NotaWordService();
        var renderer = new NotaRenderService(System.getProperty("sigaa.note.render.command"));
        var output = Files.createDirectories(Path.of("target", "nota-layout-qa"));
        String shortContent = NotaWordServiceTest.notaEjemplo().getContenido();
        String longContent = ("El equipo de trabajo solicita coordinar las actividades y verificar los acuerdos institucionales. "
            + "La información será utilizada para preparar la siguiente reunión y documentar las acciones previstas.\n").repeat(30);
        int caseNumber = 0;
        for (String content : List.of(shortContent, longContent, "Primera página.\n\f\nSegunda página.")) {
            var nota = NotaWordServiceTest.notaEjemplo(); nota.setContenido(content);
            byte[] word = wordService.generar(nota);
            long coldStart = System.nanoTime();
            byte[] pdf = renderer.pdf(word);
            var preview = renderer.vistaPrevia(word, NotaWordService.nombreArchivo(nota, "docx"));
            long coldMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - coldStart);
            long warmStart = System.nanoTime();
            var reopened = renderer.vistaPrevia(wordService.generar(nota), NotaWordService.nombreArchivo(nota, "docx"));
            long warmMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - warmStart);
            assertThat(reopened.paginas()).isSameAs(preview.paginas());
            String name = List.of("una-pagina", "varias-paginas", "salto-explicito").get(caseNumber++);
            Files.write(output.resolve(name + ".docx"), word);
            Files.write(output.resolve(name + ".pdf"), pdf);
            Files.writeString(output.resolve(name + "-tiempos.txt"), "Primera generación: " + coldMillis + " ms\nReapertura con caché: " + warmMillis + " ms\n");
            try (var document = Loader.loadPDF(pdf)) {
                assertThat(preview.totalPaginas()).isEqualTo(document.getNumberOfPages());
                assertThat(preview.paginas()).hasSize(document.getNumberOfPages());
                if (name.equals("una-pagina")) assertThat(document.getNumberOfPages()).isEqualTo(1);
                else if (name.equals("salto-explicito")) assertThat(document.getNumberOfPages()).isEqualTo(2);
                else assertThat(document.getNumberOfPages()).isGreaterThan(1);
                var stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                assertThat(text).contains("Nota. N° OAT-015-2026", "JUAN PÉREZ", "JEFE DE OPERACIONES",
                    "COMISIONADA", "ANA MARÍA DE LA CRUZ", "AMC/kjp", "atentamente,")
                    .doesNotContain("SEÑOR", "Asunto:", "Coordinación institucional");
                assertThat(text.indexOf("COMISIONADA")).isLessThan(text.indexOf("ANA MARÍA"));
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    var bounds = document.getPage(i).getMediaBox();
                    assertThat(bounds.getWidth()).isEqualTo(612);
                    assertThat(bounds.getHeight()).isEqualTo(792);
                    byte[] png = Base64.getDecoder().decode(preview.paginas().get(i).substring("data:image/png;base64,".length()));
                    var image = ImageIO.read(new ByteArrayInputStream(png));
                    assertThat(image.getWidth()).isEqualTo(816);
                    assertThat(image.getHeight()).isEqualTo(1056);
                    var actualPage = new ByteArrayOutputStream();
                    ImageIO.write(new PDFRenderer(document).renderImageWithDPI(i, 96), "png", actualPage);
                    assertThat(png).as("La vista debe ser la página del PDF descargado")
                        .containsExactly(actualPage.toByteArray());
                    Files.write(output.resolve(name + "-" + (i + 1) + ".png"), png);
                }
                if (name.equals("una-pagina")) {
                    var fonts = new PDFTextStripper() {
                        @Override protected void writeString(String text, List<TextPosition> positions) {
                            if (text.contains("Solicitamos")) {
                                assertThat(positions).allSatisfy(position -> {
                                    assertThat(position.getFont().getName()).containsIgnoringCase("Verdana");
                                    assertThat(position.getFontSizeInPt()).isEqualTo(11);
                                });
                            }
                        }
                    };
                    fonts.getText(document);
                }
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "sigaa.note.render.command", matches = ".+")
    void documentoAnuladoRealMantieneMarcaEnTodasLasPaginasAlReabrir() throws Exception {
        var nota = NotaWordServiceTest.notaEjemplo();
        nota.setEstado(NotaService.ANULADA);
        nota.setContenido("Primera página legible.\n\f\nSegunda página legible.\n\f\nTercera página legible.");
        byte[] word = new NotaWordService().generarVistaPrevia(nota);
        var renderer = new NotaRenderService(System.getProperty("sigaa.note.render.command"));
        var preview = renderer.vistaPrevia(word, NotaWordService.nombreArchivo(nota, "docx"), true);
        var output = Files.createDirectories(Path.of("target", "nota-layout-qa"));
        byte[] pdf = renderer.pdf(word, true); Files.write(output.resolve("anulada.pdf"), pdf);
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                var stripper = new PDFTextStripper(); stripper.setStartPage(i + 1); stripper.setEndPage(i + 1);
                assertThat(stripper.getText(document)).contains("ANULADA");
                Files.write(output.resolve("anulada-" + (i + 1) + ".png"),
                    Base64.getDecoder().decode(preview.paginas().get(i).substring("data:image/png;base64,".length())));
            }
        }
        assertThat(renderer.vistaPrevia(word, NotaWordService.nombreArchivo(nota, "docx"), true).paginas()).isSameAs(preview.paginas());
    }
}
