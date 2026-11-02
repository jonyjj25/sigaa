package com.sigaa.service;

import com.sigaa.model.Nota;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class NotaWordService {
    private static final String PLANTILLA = "templates/correspondencia/plantilla-nota.docx";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern(
        "d 'de' MMMM 'del' yyyy", Locale.forLanguageTag("es-PA"));

    public byte[] generar(Nota nota) {
        if (NotaService.BORRADOR.equals(nota.getEstado())) {
            throw new IllegalStateException("La nota debe oficializarse antes de generar el documento Word definitivo.");
        }
        if (NotaService.ANULADA.equals(nota.getEstado())) {
            throw new IllegalStateException("No se puede generar un documento definitivo para una nota anulada.");
        }
        if (nota.getFirmanteNombre() == null || nota.getFirmanteNombre().isBlank()) {
            throw new IllegalStateException("La nota oficializada no tiene un firmante histórico válido.");
        }
        if (nota.getFechaOficial() == null || nota.getNumero() == null) {
            throw new IllegalStateException("La nota oficializada no tiene fecha o número institucional válido.");
        }
        return generarVistaPrevia(nota);
    }

    /** Misma plantilla para el borrador, la vista paginada y el documento definitivo. */
    public byte[] generarVistaPrevia(Nota nota) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("FECHA_OFICIAL", nota.getFechaOficial() == null ? "Fecha pendiente" : "Panamá, " + FECHA.format(nota.getFechaOficial()));
        valores.put("NUMERO_NOTA", numeroInstitucional(nota));
        valores.put("DESTINATARIO_NOMBRE", mayusculas(nota.getDestinatarioNombre()));
        valores.put("DESTINATARIO_CARGO", mayusculas(nota.getDestinatarioCargo()));
        valores.put("INSTITUCION", nota.getInstitucionNombreGuardado());
        valores.put("CONTENIDO_NOTA", nota.getContenido());
        valores.put("FIRMANTE_NOMBRE", mayusculas(nota.getFirmanteNombre()));
        valores.put("FIRMANTE_CARGO", mayusculas(nota.getFirmanteCargo()));
        valores.put("FIRMANTE_INSTITUCION", nota.getFirmanteInstitucion());
        valores.put("INICIALES", iniciales(nota.getFirmanteNombre()).toUpperCase(Locale.ROOT)
            + "/" + iniciales(nombreCreador(nota)).toLowerCase(Locale.ROOT));
        return generarDesdePlantilla(PLANTILLA, valores);
    }

    /** Motor de plantillas compartido por los documentos de Correspondencia. */
    byte[] generarDesdePlantilla(String plantilla, Map<String, String> valores) {
        try {
            try (InputStream entrada = new ClassPathResource(plantilla).getInputStream()) {
                return completar(entrada.readAllBytes(), valores);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible cargar o completar la plantilla institucional de la nota.", ex);
        }
    }

    private byte[] completar(byte[] plantilla, Map<String, String> valores) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(plantilla));
             ZipOutputStream resultado = new ZipOutputStream(salida)) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                ZipEntry copia = new ZipEntry(entrada.getName());
                resultado.putNextEntry(copia);
                byte[] contenido = zip.readAllBytes();
                if (entrada.getName().startsWith("word/") && entrada.getName().endsWith(".xml")) {
                    String xml = new String(contenido, StandardCharsets.UTF_8);
                    xml = Pattern.compile("\\{\\{([A-Z_]+)}}").matcher(xml).replaceAll(match -> {
                        String clave = match.group(1);
                        return Matcher.quoteReplacement("CONTENIDO_NOTA".equals(clave) ? match.group()
                            : escaparXml(valores.getOrDefault(clave, "")));
                    });
                    if ("word/document.xml".equals(entrada.getName())) xml = completarContenido(xml, valores.get("CONTENIDO_NOTA"));
                    contenido = xml.getBytes(StandardCharsets.UTF_8);
                }
                resultado.write(contenido);
                resultado.closeEntry();
            }
        }
        return salida.toByteArray();
    }

    private String completarContenido(String xml, String texto) throws IOException {
        String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var tags = document.getElementsByTagNameNS(ns, "tag");
            for (int i = 0; i < tags.getLength(); i++) {
                Element tag = (Element) tags.item(i);
                if (!"CONTENIDO_NOTA".equals(tag.getAttributeNS(ns, "val"))) continue;
                Element sdt = (Element) tag.getParentNode().getParentNode();
                Element content = (Element) sdt.getElementsByTagNameNS(ns, "sdtContent").item(0);
                while (content.hasChildNodes()) content.removeChild(content.getFirstChild());
                String normalized = normalizarContenido(texto);
                boolean pageBreak = false;
                for (String block : normalized.replace("\f", "\n\f\n").split("\\n", -1)) {
                    if ("\f".equals(block)) { pageBreak = true; continue; }
                    if (block.isBlank()) continue;
                    Element p = document.createElementNS(ns, "w:p");
                    Element props = document.createElementNS(ns, "w:pPr");
                    Element style = document.createElementNS(ns, "w:pStyle");
                    style.setAttributeNS(ns, "w:val", "NotaContenido"); props.appendChild(style);
                    if (pageBreak) { props.appendChild(document.createElementNS(ns, "w:pageBreakBefore")); pageBreak = false; }
                    p.appendChild(props);
                    Element run = document.createElementNS(ns, "w:r");
                    Element text = document.createElementNS(ns, "w:t"); text.setTextContent(block);
                    run.appendChild(text); p.appendChild(run); content.appendChild(p);
                }
                if (!content.hasChildNodes()) content.appendChild(document.createElementNS(ns, "w:p"));
            }
            var output = new ByteArrayOutputStream();
            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(output));
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IOException("No se pudo completar el contenido editable de la nota.", ex); }
    }

    public static String normalizarContenido(String texto) {
        return texto == null ? "" : texto.replace("\r\n", "\n").replace('\r', '\n')
            .replaceAll("[\\t\\x{00A0} ]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{2,}", "\n").trim();
    }

    public static String numeroInstitucional(Nota nota) {
        if (nota.getNumero() == null) return "Pendiente";
        var numero = nota.getNumero();
        if (numero.getConsecutivo() != null) return NotaNumeroService.formatear(numero.getConsecutivo(), numero.getAnio());
        var match = Pattern.compile("^(?:N-|OAT-)?(\\d{1,3})(?:-\\d{4})?$", Pattern.CASE_INSENSITIVE).matcher(numero.getNumero());
        if (match.matches() && Integer.parseInt(match.group(1)) > 0) {
            int anio = nota.getFechaOficial() == null ? numero.getAnio() : nota.getFechaOficial().getYear();
            return NotaNumeroService.formatear(Integer.parseInt(match.group(1)), anio);
        }
        return numero.getNumero(); // Preservar identificadores históricos no numéricos.
    }

    public static String nombreArchivo(Nota nota, String extension) {
        return nombreArchivoDocumento("Nota", numeroInstitucional(nota), nota.getAsunto(), extension);
    }

    static String nombreArchivoDocumento(String tipo, String identificador, String titulo, String extension) {
        String asunto = Normalizer.normalize(titulo == null ? "" : titulo, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (asunto.length() > 100) asunto = asunto.substring(0, 100).replaceAll("-+$", "");
        String numero = identificador.replaceAll("[^A-Za-z0-9_-]", "-");
        return (tipo + "-" + numero + (asunto.isBlank() ? "" : "-" + asunto) + "." + extension).toUpperCase(Locale.ROOT);
    }

    public static String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) return "";
        String letras = java.util.Arrays.stream(nombre.trim().split("[\\s-]+"))
            .filter(word -> !java.util.Set.of("de", "del", "la", "las", "los", "y").contains(word.toLowerCase(Locale.ROOT)))
            .filter(word -> Character.isLetter(word.codePointAt(0)))
            .map(word -> new String(Character.toChars(Character.toUpperCase(word.codePointAt(0)))))
            .collect(java.util.stream.Collectors.joining());
        if (letras.isEmpty()) return "";
        int primeraLetra = Character.charCount(letras.codePointAt(0));
        return letras.substring(0, primeraLetra) + letras.substring(primeraLetra).toLowerCase(Locale.ROOT);
    }

    private String nombreCreador(Nota nota) {
        var usuario = nota.getCreadoPor();
        return usuario == null ? "" : String.join(" ", usuario.getNombre() == null ? "" : usuario.getNombre(),
            usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(), usuario.getApellido() == null ? "" : usuario.getApellido());
    }

    private String mayusculas(String texto) { return texto == null ? "" : texto.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT); }

    private String escaparXml(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
