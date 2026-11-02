package com.sigaa.service;

import com.sigaa.model.Nota;
import com.sigaa.model.NotaNumero;
import com.sigaa.model.Organization;
import com.sigaa.model.Usuario;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotaWordServiceTest {
    @Test
    void generaDocxConDatosPersistidosYProteccionDeFormulario() throws Exception {
        NotaNumero numero = new NotaNumero(); numero.setNumero("N-042"); numero.setEstado("OCUPADO");
        Organization institucion = new Organization(); institucion.setNombre("Ministerio de Seguridad Pública");
        Nota nota = new Nota(); nota.setNumero(numero); nota.setEstado("EN_SEGUIMIENTO");
        nota.setFechaOficial(LocalDate.of(2026, 9, 28)); nota.setAsunto("Coordinación institucional");
        nota.setDestinatarioNombre("Juan Pérez"); nota.setDestinatarioCargo("Comisionado"); nota.setInstitucion(institucion);
        nota.setContenido("Primera línea\nSegunda línea"); nota.setFirmanteNombre("Ana Pérez"); nota.setFirmanteCargo("Directora");
        nota.setFirmanteInstitucion("Autoridad Nacional de Transparencia");

        byte[] generado = new NotaWordService().generar(nota);

        assertThat(generado).isNotEmpty();
        String documentXml = entrada(generado, "word/document.xml");
        String settingsXml = entrada(generado, "word/settings.xml");
        assertThat(documentXml).contains("Panamá, 28 de septiembre del 2026", "Nota. N° OAT-042-2026", "JUAN PÉREZ", "ANA PÉREZ", "Primera línea",
            "Autoridad Nacional de Transparencia", "Sistema Nacional de AA");
        assertThat(documentXml).doesNotContain("SEÑOR", "Asunto:", "Coordinación institucional");
        assertThat(documentXml).doesNotContain("{{FECHA_OFICIAL}}", "{{CONTENIDO_NOTA}}");
        assertThat(documentXml).contains("w:tag w:val=\"CONTENIDO_NOTA\"");
        assertThat(settingsXml).contains("w:documentProtection", "w:edit=\"forms\"", "w:enforcement=\"1\"");
    }

    @Test
    void formatoCartaVerdanaOnceConDestinatarioYFirmaEnNegritaYOrdenInstitucional() throws Exception {
        byte[] docx = new NotaWordService().generar(notaEjemplo());
        String xml = entrada(docx, "word/document.xml");
        String styles = entrada(docx, "word/styles.xml");
        assertThat(xml).contains("w:w=\"12240\"", "w:h=\"15840\"");
        assertThat(styles).contains("w:ascii=\"Verdana\"", "w:sz w:val=\"22\"", "w:jc w:val=\"both\"");
        var document = parse(xml);
        for (String tag : new String[] {"DESTINATARIO_NOMBRE", "DESTINATARIO_CARGO", "FIRMANTE_CARGO", "FIRMANTE_NOMBRE"}) {
            Element control = control(document, tag);
            assertThat(control.getElementsByTagNameNS(NS, "b").getLength()).as(tag).isEqualTo(1);
            String text = control.getTextContent();
            assertThat(text).isEqualTo(text.toUpperCase(java.util.Locale.ROOT));
        }
        assertThat(xml.indexOf("COMISIONADA")).isLessThan(xml.indexOf("ANA MARÍA DE LA CRUZ"));
        assertThat(control(document, "INICIALES").getTextContent()).isEqualTo("AMC/kjp");
        assertThat(xml).contains("w:sz w:val=\"16\"");
        var paragraphs = document.getElementsByTagNameNS(NS, "p");
        Element greeting = java.util.stream.IntStream.range(0, paragraphs.getLength())
            .mapToObj(i -> (Element) paragraphs.item(i)).filter(p -> "atentamente,".equals(p.getTextContent()))
            .findFirst().orElseThrow();
        Element spacing = (Element) greeting.getElementsByTagNameNS(NS, "spacing").item(0);
        assertThat(spacing.getAttributeNS(NS, "after")).isEqualTo("1080");
        assertThat(greeting.getElementsByTagNameNS(NS, "keepNext").getLength()).isEqualTo(1);
    }

    @Test
    void inicialesDistinguenFirmanteYElaboradorYSeCalculanConSusNombres() throws Exception {
        Nota nota = notaEjemplo(); nota.setFirmanteNombre("Firma de Prueba");
        Usuario creador = new Usuario(); creador.setNombre("Persona"); creador.setSegundoNombre("Elaboradora"); creador.setApellido("Ficticia");
        nota.setCreadoPor(creador); NotaWordService service = new NotaWordService();
        assertThat(control(parse(entrada(service.generar(nota), "word/document.xml")), "INICIALES").getTextContent()).isEqualTo("FP/pef");
        nota.setFirmanteNombre("Otro Firmante"); creador.setNombre("Autor"); creador.setSegundoNombre(null); creador.setApellido("Ejemplo");
        assertThat(control(parse(entrada(service.generar(nota), "word/document.xml")), "INICIALES").getTextContent()).isEqualTo("OF/ae");
        nota.setEstado("BORRADOR");
        assertThat(control(parse(entrada(service.generarVistaPrevia(nota), "word/document.xml")), "INICIALES").getTextContent()).isEqualTo("OF/ae");
    }

    @Test
    void contenidoEditableUsaParrafosRealesYUnSoloEspacioEntreBloques() throws Exception {
        Nota nota = notaEjemplo();
        nota.setContenido("  Uno  & <dos>\r\n\r\n\r\n  Segundo   párrafo\n\f\nTercero {{ASUNTO}} ");
        String xml = entrada(new NotaWordService().generar(nota), "word/document.xml");
        Element content = control(parse(xml), "CONTENIDO_NOTA");
        var paragraphs = content.getElementsByTagNameNS(NS, "p");
        assertThat(paragraphs.getLength()).isEqualTo(3);
        assertThat(paragraphs.item(0).getTextContent()).isEqualTo("Uno & <dos>");
        assertThat(paragraphs.item(1).getTextContent()).isEqualTo("Segundo párrafo");
        assertThat(paragraphs.item(2).getTextContent()).isEqualTo("Tercero {{ASUNTO}}");
        assertThat(content.getElementsByTagNameNS(NS, "br").getLength()).isZero();
        assertThat(content.getElementsByTagNameNS(NS, "lock").getLength()).isZero();
        assertThat(content.getElementsByTagNameNS(NS, "pageBreakBefore").getLength()).isEqualTo(1);
        nota.setContenido("Texto editado antes de generar");
        assertThat(entrada(new NotaWordService().generar(nota), "word/document.xml"))
            .contains("Texto editado antes de generar").doesNotContain("Segundo párrafo");
    }

    @Test
    void nombreDeArchivoUsaAsuntoSeguroConReferenciaAlNumero() {
        Nota nota = notaEjemplo();
        nota.setAsunto("Coordinación: reunión / gestión <2026>? \"final\"");
        assertThat(NotaWordService.nombreArchivo(nota, "docx"))
            .isEqualTo("NOTA-OAT-015-2026-COORDINACION-REUNION-GESTION-2026-FINAL.DOCX");
        assertThat(NotaWordService.nombreArchivo(nota, "pdf")).endsWith(".PDF");
        assertThat(NotaWordService.iniciales("Ana María de la Cruz")).isEqualTo("Amc");
        assertThat(NotaWordService.iniciales("KEICY JOSÉ PÉREZ")).isEqualTo("Kjp");
        assertThat(NotaWordService.iniciales("Érika de Ávila")).isEqualTo("Éá");
        assertThat(NotaWordService.iniciales("Ana")).isEqualTo("A");
        assertThat(NotaWordService.iniciales("")).isEmpty();
    }

    @Test
    void vistaDeBorradorYDescargaUsanLaMismaPlantillaSinPermitirDocumentoDefinitivoAntesDeOficializar() throws Exception {
        Nota nota = notaEjemplo();
        NotaWordService service = new NotaWordService();
        String oficial = entrada(service.generar(nota), "word/document.xml");
        nota.setEstado("BORRADOR");
        assertThat(entrada(service.generarVistaPrevia(nota), "word/document.xml")).isEqualTo(oficial);
        assertThatThrownBy(() -> service.generar(nota)).hasMessageContaining("oficializarse");
        nota.setEstado("ANULADA");
        assertThatThrownBy(() -> service.generar(nota)).hasMessageContaining("anulada");
    }

    @Test
    void documentoConservaLaInstitucionGuardadaAunqueElCatalogoCambie() throws Exception {
        Nota nota = notaEjemplo();
        nota.setDestino("Institución original de prueba");
        nota.getInstitucion().setNombre("Institución renombrada de prueba");
        String xml = entrada(new NotaWordService().generar(nota), "word/document.xml");
        assertThat(xml).contains("Institución original de prueba").doesNotContain("Institución renombrada de prueba");
    }

    static Nota notaEjemplo() {
        NotaNumero numero = new NotaNumero(); numero.setNumero("OAT-015-2026");
        numero.setConsecutivo(15); numero.setAnio(2026); numero.setEstado("OCUPADO");
        Organization institution = new Organization(); institution.setNombre("Ministerio de Seguridad Pública");
        Usuario creator = new Usuario(); creator.setNombre("Keicy"); creator.setSegundoNombre("José"); creator.setApellido("Pérez");
        Nota nota = new Nota(); nota.setNumero(numero); nota.setEstado("EN_SEGUIMIENTO");
        nota.setFechaOficial(LocalDate.of(2026, 10, 6)); nota.setAsunto("Coordinación institucional");
        nota.setDestinatarioNombre("Juan Pérez"); nota.setDestinatarioCargo("Jefe de operaciones");
        nota.setInstitucion(institution); nota.setContenido("Solicitamos su colaboración para coordinar las actividades institucionales.\nAgradecemos su atención y quedamos atentos a su respuesta.");
        nota.setFirmanteNombre("Ana María de la Cruz"); nota.setFirmanteCargo("Comisionada");
        nota.setFirmanteInstitucion("Institución de prueba"); nota.setCreadoPor(creator);
        return nota;
    }

    private static final String NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private org.w3c.dom.Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    private Element control(org.w3c.dom.Document document, String name) {
        var tags = document.getElementsByTagNameNS(NS, "tag");
        for (int i = 0; i < tags.getLength(); i++) {
            Element tag = (Element) tags.item(i);
            if (name.equals(tag.getAttributeNS(NS, "val"))) return (Element) tag.getParentNode().getParentNode();
        }
        throw new AssertionError("Control inexistente: " + name);
    }
    static String entrada(byte[] docx, String nombre) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (nombre.equals(entry.getName())) return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("No existe " + nombre);
    }
}
