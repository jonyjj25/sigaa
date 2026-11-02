package com.sigaa.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Respaldo autocontenido para las notas cuando LibreOffice no está instalado.
 * La entrada es siempre el DOCX creado por {@link NotaWordService}; no admite
 * archivos suministrados por usuarios.
 */
final class NotaPdfFallback {
    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final PDRectangle PAGE = PDRectangle.LETTER;
    private static final float LEFT = 72;
    private static final float RIGHT = 72;
    private static final float BODY_TOP = 708;
    private static final float BODY_BOTTOM = 62;
    private static final float FONT_SIZE = 11;
    private static final float LINE_HEIGHT = 13.5f;
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private NotaPdfFallback() { }

    static byte[] convertir(byte[] word) {
        List<Paragraph> paragraphs = paragraphs(word);
        if (paragraphs.isEmpty()) throw new IllegalStateException("El documento Word de la nota no contiene texto.");
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            PageWriter writer = new PageWriter(document);
            for (Paragraph paragraph : paragraphs) writer.paragraph(paragraph);
            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible generar el PDF integrado de la nota.", ex);
        }
    }

    private static List<Paragraph> paragraphs(byte[] word) {
        byte[] xml = documentXml(word);
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            var nodes = document.getElementsByTagNameNS(WORD_NS, "p");
            List<Paragraph> result = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element paragraph = (Element) nodes.item(i);
                String text = text(paragraph).trim();
                if (text.isEmpty()) continue;
                Element properties = firstChild(paragraph, "pPr");
                String style = property(properties, "pStyle", "val");
                String alignment = property(properties, "jc", "val");
                boolean pageBreak = properties != null
                    && properties.getElementsByTagNameNS(WORD_NS, "pageBreakBefore").getLength() > 0;
                float after = twips(property(properties, "spacing", "after"));
                boolean bold = isBold(paragraph);
                result.add(new Paragraph(text, bold, "right".equals(alignment),
                    "center".equals(alignment), pageBreak, "NotaContenido".equals(style), after));
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible leer el contenido de la nota para generar su PDF.", ex);
        }
    }

    private static byte[] documentXml(byte[] word) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(word))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) return zip.readAllBytes();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible abrir el documento Word de la nota.", ex);
        }
        throw new IllegalStateException("El documento Word de la nota no contiene word/document.xml.");
    }

    private static String text(Element paragraph) {
        StringBuilder result = new StringBuilder();
        appendText(paragraph, result);
        return result.toString();
    }

    private static void appendText(Node node, StringBuilder result) {
        if (node instanceof Element element && WORD_NS.equals(element.getNamespaceURI())) {
            if ("t".equals(element.getLocalName())) result.append(element.getTextContent());
            else if ("tab".equals(element.getLocalName())) result.append(' ');
        }
        if (node.hasChildNodes() && !(node instanceof Element element && "t".equals(element.getLocalName()))) {
            for (int i = 0; i < node.getChildNodes().getLength(); i++) appendText(node.getChildNodes().item(i), result);
        }
    }

    private static Element firstChild(Element parent, String name) {
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            Node node = parent.getChildNodes().item(i);
            if (node instanceof Element element && WORD_NS.equals(element.getNamespaceURI())
                    && name.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static String property(Element properties, String elementName, String attributeName) {
        if (properties == null) return "";
        var values = properties.getElementsByTagNameNS(WORD_NS, elementName);
        if (values.getLength() == 0) return "";
        return ((Element) values.item(0)).getAttributeNS(WORD_NS, attributeName);
    }

    private static float twips(String value) {
        try { return value == null || value.isBlank() ? 0 : Float.parseFloat(value) / 20f; }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static boolean isBold(Element paragraph) {
        var values = paragraph.getElementsByTagNameNS(WORD_NS, "b");
        for (int i = 0; i < values.getLength(); i++) {
            String value = ((Element) values.item(i)).getAttributeNS(WORD_NS, "val");
            if (!"0".equals(value) && !"false".equalsIgnoreCase(value) && !"off".equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static String printable(String value, PDFont font) {
        String normalized = value.replace('\u00a0', ' ').replace('\u2010', '-').replace('\u2011', '-')
            .replace('\u2012', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201c', '"').replace('\u201d', '"');
        StringBuilder result = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            try { font.encode(character); result.append(character); }
            catch (IOException | IllegalArgumentException ex) { result.append('?'); }
        });
        return result.toString();
    }

    private record Paragraph(String text, boolean bold, boolean right, boolean center,
                             boolean pageBreak, boolean content, float after) { }

    private static final class PageWriter {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        private PageWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void paragraph(Paragraph paragraph) throws IOException {
            if (paragraph.pageBreak() && y < BODY_TOP) newPage();
            PDFont font = paragraph.bold() ? BOLD : REGULAR;
            List<String> lines = wrap(printable(paragraph.text(), font), font, FONT_SIZE, PAGE.getWidth() - LEFT - RIGHT);
            float required = lines.size() * LINE_HEIGHT + Math.max(paragraph.after(), paragraph.content() ? 11 : 0);
            // La despedida y el bloque de firma deben permanecer juntos.
            if ("ATENTAMENTE,".equalsIgnoreCase(paragraph.text())
                    && y - required - (5 * LINE_HEIGHT) < BODY_BOTTOM) newPage();
            if (y - required < BODY_BOTTOM) newPage();
            for (String line : lines) {
                if (y - LINE_HEIGHT < BODY_BOTTOM) newPage();
                float width = font.getStringWidth(line) / 1000 * FONT_SIZE;
                float x = paragraph.right() ? PAGE.getWidth() - RIGHT - width
                    : paragraph.center() ? (PAGE.getWidth() - width) / 2 : LEFT;
                write(line, font, FONT_SIZE, x, y);
                y -= LINE_HEIGHT;
            }
            y -= Math.max(paragraph.after(), paragraph.content() ? 11 : 0);
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            var page = new PDPage(PAGE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            centered("SIGAA", BOLD, 13, 760);
            centered("Nota institucional", REGULAR, 9, 744);
            centered("Documento generado por SIGAA", REGULAR, 8, 32);
            y = BODY_TOP;
        }

        private void centered(String value, PDFont font, float size, float baseline) throws IOException {
            float width = font.getStringWidth(value) / 1000 * size;
            write(value, font, size, (PAGE.getWidth() - width) / 2, baseline);
        }

        private void write(String value, PDFont font, float size, float x, float baseline) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(value);
            stream.endText();
        }

        private void close() throws IOException {
            if (stream != null) { stream.close(); stream = null; }
        }

        private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (font.getStringWidth(word) / 1000 * size <= maxWidth) {
                    if (!current.isEmpty()) current.append(' ');
                    current.append(word);
                } else {
                    for (int offset = 0; offset < word.length();) {
                        int end = offset + 1;
                        while (end <= word.length()
                                && font.getStringWidth(word.substring(offset, end)) / 1000 * size <= maxWidth) end++;
                        end = Math.max(offset + 1, end - 1);
                        if (!current.isEmpty()) { lines.add(current.toString()); current.setLength(0); }
                        lines.add(word.substring(offset, end));
                        offset = end;
                    }
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
            if (lines.isEmpty()) lines.add("");
            return lines;
        }
    }
}
