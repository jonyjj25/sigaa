package com.sigaa.service;

import com.sigaa.util.LogDiagnostics;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Usa LibreOffice cuando está disponible y un generador PDF integrado como respaldo. */
@Service
public class NotaRenderService {
    private static final Logger log = LoggerFactory.getLogger(NotaRenderService.class);
    private static final int MAX_CACHE_ENTRIES = 12;
    private static final long MAX_CACHE_BYTES = 32L * 1024 * 1024;
    private static final long CACHE_TTL_NANOS = Duration.ofMinutes(5).toNanos();
    private final String command;
    private final Semaphore conversions = new Semaphore(2, true);
    private final Object cacheLock = new Object();
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, .75f, true);
    private final ConcurrentHashMap<String, CompletableFuture<RenderedDocument>> inFlight = new ConcurrentHashMap<>();
    private long cacheBytes;

    public NotaRenderService(@Value("${app.preview.libreoffice-command:}") String command) { this.command = command; }

    public byte[] pdf(byte[] word) { return pdf(word, false); }

    public byte[] pdf(byte[] word, boolean anulada) { return rendered(word, anulada ? "ANULADA" : "").pdf.clone(); }

    public VistaPrevia vistaPrevia(byte[] word, String nombreArchivo) { return vistaPrevia(word, nombreArchivo, false); }

    public VistaPrevia vistaPrevia(byte[] word, String nombreArchivo, boolean anulada) {
        return vistaPrevia(word, nombreArchivo, anulada ? "ANULADA" : "");
    }

    public VistaPrevia vistaPrevia(byte[] word, String nombreArchivo, String marca) {
        if (!marca.isEmpty() && !marca.equals("ANULADA") && !marca.equals("ANULADO")) throw new IllegalArgumentException("Marca documental no válida.");
        String key = cacheKey(word, marca);
        var rendered = rendered(word, marca, key);
        List<String> pages = rendered.pages();
        remember(key, rendered);
        return new VistaPrevia(pages, pages.size(), nombreArchivo);
    }

    private RenderedDocument rendered(byte[] word, String marca) {
        return rendered(word, marca, cacheKey(word, marca));
    }

    private RenderedDocument rendered(byte[] word, String marca, String key) {
        synchronized (cacheLock) {
            evictExpired();
            var cached = cache.get(key);
            if (cached != null) return cached.document;
        }
        // Solicitudes concurrentes del mismo contenido comparten una sola conversión.
        var pending = new CompletableFuture<RenderedDocument>();
        var existing = inFlight.putIfAbsent(key, pending);
        if (existing != null) {
            try { return existing.join(); }
            catch (CompletionException ex) {
                if (ex.getCause() instanceof RuntimeException cause) throw cause;
                throw ex;
            }
        }
        try {
            // Otra solicitud puede haber completado entre la consulta de caché y putIfAbsent.
            synchronized (cacheLock) {
                evictExpired();
                var cached = cache.get(key);
                if (cached != null) { pending.complete(cached.document); return cached.document; }
            }
            byte[] pdf = !marca.isEmpty() ? rendered(word, "").pdf : convertirPdf(word);
            var result = new RenderedDocument(!marca.isEmpty() ? identificarAnulada(pdf, marca) : pdf);
            remember(key, result);
            pending.complete(result);
            return result;
        } catch (RuntimeException | Error ex) {
            pending.completeExceptionally(ex);
            throw ex;
        } finally { inFlight.remove(key, pending); }
    }

    /** Hash de los archivos internos, sin fechas ni orden de entradas ZIP que no cambian el documento. */
    private String cacheKey(byte[] word, String marca) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            Map<String, byte[]> entries = new TreeMap<>();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(word))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory()) entries.put(entry.getName(), zip.readAllBytes());
                }
            }
            if (entries.isEmpty()) digest.update(word);
            for (var entry : entries.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(name.length).array()); digest.update(name);
                digest.update(ByteBuffer.allocate(4).putInt(entry.getValue().length).array()); digest.update(entry.getValue());
            }
            digest.update(marca.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) { throw new IllegalStateException("No se pudo leer el documento de la nota.", ex); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("No se pudo identificar el documento de la nota.", ex); }
    }

    private void remember(String key, RenderedDocument document) {
        synchronized (cacheLock) {
            evictExpired();
            CacheEntry previous = cache.remove(key);
            if (previous != null) cacheBytes -= previous.bytes;
            long weight = document.bytes();
            if (weight > MAX_CACHE_BYTES) return;
            cache.put(key, new CacheEntry(document, previous == null ? System.nanoTime() : previous.created, weight));
            cacheBytes += weight;
            var iterator = cache.entrySet().iterator();
            while ((cache.size() > MAX_CACHE_ENTRIES || cacheBytes > MAX_CACHE_BYTES) && iterator.hasNext()) {
                cacheBytes -= iterator.next().getValue().bytes; iterator.remove();
            }
        }
    }

    private void evictExpired() {
        long now = System.nanoTime();
        var iterator = cache.values().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.created >= CACHE_TTL_NANOS) { cacheBytes -= entry.bytes; iterator.remove(); }
        }
    }

    /** El método se separa para comprobar la caché sin requerir procesos externos en cada prueba. */
    protected byte[] convertirPdf(byte[] word) {
        Path temp = null;
        Process process = null;
        boolean acquired = false;
        try {
            acquired = conversions.tryAcquire(45, TimeUnit.SECONDS);
            if (!acquired) throw new IllegalStateException("El generador de notas está ocupado. Intenta nuevamente.");
            if (command == null || command.isBlank()) return NotaPdfFallback.convertir(word);
            temp = Files.createTempDirectory("sigaa-nota-");
            Path source = temp.resolve("nota.docx");
            Files.write(source, word);
            Path profile = Files.createDirectories(temp.resolve("profile"));
            String executable = command;
            Path configured = Path.of(command);
            if (configured.getFileName() != null && "soffice.exe".equalsIgnoreCase(configured.getFileName().toString())
                    && Files.isRegularFile(configured.resolveSibling("soffice.com"))) {
                executable = configured.resolveSibling("soffice.com").toString();
            }
            process = new ProcessBuilder(executable, "-env:UserInstallation=" + profile.toUri(), "--headless",
                "--convert-to", "pdf", "--outdir", temp.toString(), source.toString())
                .redirectErrorStream(true).redirectOutput(temp.resolve("conversion.log").toFile()).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                log.warn("LibreOffice excedió el tiempo disponible; se usará el generador PDF integrado.");
                return NotaPdfFallback.convertir(word);
            }
            Path pdf = temp.resolve("nota.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf)) {
                log.warn("LibreOffice no generó la nota (código {}); se usará el generador PDF integrado.", process.exitValue());
                return NotaPdfFallback.convertir(word);
            }
            return Files.readAllBytes(pdf);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La generación de la nota fue interrumpida.", ex);
        } catch (IOException ex) {
            log.warn("LibreOffice no está disponible; se usará el generador PDF integrado: {}", ex.getMessage());
            try { return NotaPdfFallback.convertir(word); }
            catch (RuntimeException fallback) {
                fallback.addSuppressed(ex);
                throw new IllegalStateException("No fue posible convertir la nota a PDF.", fallback);
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(5, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            if (temp != null) {
                try (var paths = Files.walk(temp)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ex) { log.warn("No se pudo limpiar un archivo temporal de nota: {}", LogDiagnostics.describe(ex)); }
                    });
                } catch (IOException ex) { log.warn("No se pudo limpiar la carpeta temporal de nota: {}", LogDiagnostics.describe(ex)); }
            }
            if (acquired) conversions.release();
        }
    }

    private byte[] identificarAnulada(byte[] pdf, String marca) {
        try (var document = Loader.loadPDF(pdf); var output = new ByteArrayOutputStream()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float fontSize = 82;
            float width = font.getStringWidth(marca) / 1000 * fontSize;
            for (var page : document.getPages()) {
                var box = page.getMediaBox();
                try (var stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    var state = new PDExtendedGraphicsState(); state.setNonStrokingAlphaConstant(.18f);
                    stream.setGraphicsStateParameters(state); stream.setNonStrokingColor(.4f);
                    double angle = Math.PI / 4;
                    stream.beginText(); stream.setFont(font, fontSize);
                    stream.setTextMatrix(Matrix.getRotateInstance(angle,
                        (float) (box.getLowerLeftX() + box.getWidth() / 2 - Math.cos(angle) * width / 2),
                        (float) (box.getLowerLeftY() + box.getHeight() / 2 - Math.sin(angle) * width / 2)));
                    stream.showText(marca); stream.endText();
                }
            }
            document.save(output); return output.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("No fue posible identificar la nota anulada.", ex); }
    }

    private static final class RenderedDocument {
        private final byte[] pdf;
        private volatile List<String> images;
        private RenderedDocument(byte[] pdf) {
            try (var document = Loader.loadPDF(pdf)) {
                if (document.getNumberOfPages() == 0) throw new IllegalStateException("El PDF de la nota no contiene páginas.");
            } catch (IOException ex) { throw new IllegalStateException("El generador no devolvió un PDF válido para la nota.", ex); }
            this.pdf = pdf;
        }

        private synchronized List<String> pages() {
            if (images != null) return images;
            try (var document = Loader.loadPDF(pdf)) {
                if (document.getNumberOfPages() > 50) throw new IllegalArgumentException("La vista previa admite hasta 50 páginas. Revise los saltos del contenido.");
                var renderer = new PDFRenderer(document);
                List<String> pages = new ArrayList<>();
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    var output = new ByteArrayOutputStream();
                    ImageIO.write(renderer.renderImageWithDPI(i, 96), "png", output);
                    pages.add("data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()));
                }
                images = List.copyOf(pages); return images;
            } catch (IOException ex) { throw new IllegalStateException("No fue posible representar las páginas de la nota.", ex); }
        }

        private long bytes() { return pdf.length + (images == null ? 0 : images.stream().mapToLong(page -> page.length() * 2L).sum()); }
    }

    private record CacheEntry(RenderedDocument document, long created, long bytes) { }
    public record VistaPrevia(List<String> paginas, int totalPaginas, String nombreArchivo) { }
}
