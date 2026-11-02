package com.sigaa.util;

import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileContentValidator {
    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "webp", "doc", "xls", "ppt",
            "docx", "xlsx", "pptx", "zip", "rar", "txt", "csv");

    private FileContentValidator() {}

    public static String validate(MultipartFile file, Set<String> allowedExtensions) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        try (InputStream input = file.getInputStream()) {
            return validate(filename, input, allowedExtensions);
        }
    }

    public static String validate(String filename, InputStream source, Set<String> allowedExtensions) throws IOException {
        String extension = extensionOf(filename);
        if (extension.isBlank() || !allowedExtensions.contains(extension)) {
            throw new IOException("Extensión de archivo no permitida");
        }
        BufferedInputStream input = source instanceof BufferedInputStream buffered ? buffered : new BufferedInputStream(source);
        input.mark(64 * 1024);
        byte[] header = input.readNBytes(16);
        input.reset();
        if (header.length == 0) throw new IOException("El archivo está vacío");

        boolean valid = switch (extension) {
            case "pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "jpg", "jpeg" -> startsWith(header, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "gif" -> startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "webp" -> header.length >= 12 && ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 4).equals("WEBP");
            case "doc", "xls", "ppt" -> startsWith(header, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
            case "docx", "xlsx", "pptx" -> isExpectedOfficeZip(input, extension);
            case "zip" -> isZip(header);
            case "rar" -> startsWith(header, new byte[]{0x52, 0x61, 0x72, 0x21, 0x1a, 0x07});
            case "txt", "csv" -> looksLikeText(input);
            default -> false;
        };
        if (!valid) throw new IOException("El contenido del archivo no coincide con su extensión");
        return extension;
    }

    public static String extensionOf(String filename) {
        String safe = filename.replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        int dot = safe.lastIndexOf('.');
        return dot > slash && dot < safe.length() - 1 ? safe.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static boolean isExpectedOfficeZip(InputStream input, String extension) throws IOException {
        String requiredPrefix = switch (extension) {
            case "docx" -> "word/";
            case "xlsx" -> "xl/";
            case "pptx" -> "ppt/";
            default -> "";
        };
        try (ZipInputStream zip = new ZipInputStream(input)) {
            boolean contentTypes = false;
            boolean expectedFolder = false;
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 5000) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                contentTypes |= "[content_types].xml".equals(name);
                expectedFolder |= name.startsWith(requiredPrefix);
                if (contentTypes && expectedFolder) return true;
            }
            return false;
        }
    }

    private static boolean looksLikeText(InputStream input) throws IOException {
        byte[] sample = input.readNBytes(8192);
        for (byte value : sample) {
            int unsigned = value & 0xff;
            if (unsigned == 0 || (unsigned < 0x09) || (unsigned > 0x0d && unsigned < 0x20)) return false;
        }
        return true;
    }

    private static boolean isZip(byte[] header) {
        return startsWith(header, new byte[]{0x50, 0x4b, 0x03, 0x04})
                || startsWith(header, new byte[]{0x50, 0x4b, 0x05, 0x06})
                || startsWith(header, new byte[]{0x50, 0x4b, 0x07, 0x08});
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }

    private static String ascii(byte[] value, int offset, int length) {
        return new String(value, offset, length, StandardCharsets.US_ASCII);
    }
}
