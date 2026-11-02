package com.sigaa.service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

class CorrespondenciaPlantillaRenderTest {
    @Test @EnabledIfSystemProperty(named = "sigaa.note.render.command", matches = ".+")
    void notasYMemosRepitenElDisenoSinInvadirloYLaVistaEsElMismoPdf() throws Exception {
        var word = new NotaWordService(); var memoWord = new MemoWordService(word);
        var memo = MemoWordServiceTest.ejemplo();
        var memoVarias = MemoWordServiceTest.ejemplo(); memoVarias.setDescripcion("Primera página de prueba.\n\f\nSegunda página de prueba.");
        var nota = NotaWordServiceTest.notaEjemplo();
        nota.setDestinatarioNombre("Persona receptora de prueba"); nota.setDestinatarioCargo("Cargo de prueba");
        nota.setFirmanteNombre("Persona firmante de prueba"); nota.setFirmanteCargo("Dirección de prueba");
        var creador = new com.sigaa.model.Usuario(); creador.setNombre("Elaborador"); creador.setApellido("Ficticio"); nota.setCreadoPor(creador);
        var output = Files.createDirectories(Path.of("target", "correspondencia-plantilla-qa"));
        var renderer = new NotaRenderService(System.getProperty("sigaa.note.render.command"));
        byte[][] fuentes = {word.generar(nota), memoWord.generar(memo), memoWord.generar(memoVarias)};
        String[] nombres = {"nota-institucional", "memo-institucional", "memo-dos-paginas"};
        try (var original = Loader.loadPDF(getClass().getResourceAsStream("/templates/correspondencia/membrete-2026.pdf").readAllBytes())) {
            var referencia = new PDFRenderer(original).renderImageWithDPI(0, 96);
            for (int d = 0; d < fuentes.length; d++) {
                var vista = renderer.vistaPrevia(fuentes[d], nombres[d] + ".DOCX");
                byte[] pdf = renderer.pdf(fuentes[d]);
                Files.write(output.resolve(nombres[d] + ".docx"), fuentes[d]); Files.write(output.resolve(nombres[d] + ".pdf"), pdf);
                try (var documento = Loader.loadPDF(pdf)) {
                    assertThat(documento.getNumberOfPages()).isEqualTo(d == 2 ? 2 : 1);
                    assertThat(vista.totalPaginas()).isEqualTo(documento.getNumberOfPages());
                    var posiciones = new PDFTextStripper() {
                        @Override protected void writeString(String texto, List<TextPosition> chars) {
                            assertThat(chars).allSatisfy(pos -> assertThat(pos.getYDirAdj()).isBetween(100f, 690f));
                        }
                    };
                    posiciones.getText(documento);
                    for (int p = 0; p < documento.getNumberOfPages(); p++) {
                        assertThat(documento.getPage(p).getMediaBox().getWidth()).isEqualTo(612);
                        assertThat(documento.getPage(p).getMediaBox().getHeight()).isEqualTo(792);
                        byte[] png = Base64.getDecoder().decode(vista.paginas().get(p).substring("data:image/png;base64,".length()));
                        var pagina = ImageIO.read(new ByteArrayInputStream(png));
                        var descargada = new PDFRenderer(documento).renderImageWithDPI(p, 96);
                        assertThat(java.util.Arrays.equals(pagina.getRGB(0, 0, 816, 1056, null, 0, 816),
                            descargada.getRGB(0, 0, 816, 1056, null, 0, 816)))
                            .as("La vista previa coincide píxel a píxel con el PDF descargado").isTrue();
                        double diferencia = 0; int muestras = 0;
                        for (int y = 0; y < 1056; y += 3) {
                            if (y > 132 && y < 924) continue;
                            for (int x = 0; x < 816; x += 3) {
                                int a = pagina.getRGB(x,y), b = referencia.getRGB(x,y);
                                for (int shift : new int[] {0,8,16}) { diferencia += Math.abs(((a >> shift) & 255) - ((b >> shift) & 255)); muestras++; }
                            }
                        }
                        assertThat(diferencia / muestras).as("Fidelidad del membrete y pie en cada página").isLessThan(8);
                        Files.write(output.resolve(nombres[d] + "-" + (p+1) + ".png"), png);
                    }
                }
            }
        }
    }
}
