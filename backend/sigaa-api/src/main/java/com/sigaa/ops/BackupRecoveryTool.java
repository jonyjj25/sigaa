package com.sigaa.ops;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Herramienta offline: no inicia Spring, no lee .env ni conecta una base al extraer. */
public final class BackupRecoveryTool {
    private static final byte[] FULL_MAGIC = "SIGAAFULL1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DB_MAGIC = "SIGAABK1".getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_EXTRACT_BYTES = 100L * 1024 * 1024 * 1024;
    private static final int FRAME_BYTES = 1024 * 1024;
    private BackupRecoveryTool() {}

    public static void main(String[] args) {
        try {
            byte[] key = Base64.getDecoder().decode(required("BACKUP_ENCRYPTION_KEY_BASE64"));
            switch (args.length == 0 ? "" : args[0]) {
                case "create" -> {
                    if (args.length != 2) throw new IllegalArgumentException();
                    createFromDatabase(Path.of(args[1]), key);
                }
                case "extract" -> {
                    if (args.length != 3) throw new IllegalArgumentException();
                    extract(Path.of(args[1]), Path.of(args[2]), key);
                }
                case "decrypt-db" -> {
                    if (args.length != 3) throw new IllegalArgumentException();
                    decryptDatabase(Path.of(args[1]), Path.of(args[2]), key);
                }
                default -> throw new IllegalArgumentException("Use create <copia>, extract <copia> <directorio nuevo> o decrypt-db <copia BD> <archivo nuevo>");
            }
            System.out.println("Operación de respaldo completada y verificada.");
        } catch (Exception exception) {
            // No imprimir URLs, claves, salida SQL ni rutas potencialmente privadas.
            System.err.println("No se completó la operación de respaldo: " + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta configuración de respaldo");
        return value;
    }

    private static void createFromDatabase(Path output, byte[] key) throws Exception {
        URI database = URI.create(required("DB_URL").replaceFirst("^jdbc:", ""));
        if (!"postgresql".equals(database.getScheme()) || database.getHost() == null) throw new IllegalArgumentException();
        Path dump = Files.createTempFile(Path.of(required("BACKUP_DIR")), "sigaa-full-", ".dump.tmp");
        try {
            ProcessBuilder builder = new ProcessBuilder(System.getenv().getOrDefault("BACKUP_PG_DUMP", "pg_dump"),
                "-h", database.getHost(), "-p", Integer.toString(database.getPort() < 0 ? 5432 : database.getPort()),
                "-U", required("BACKUP_DB_USERNAME"), "--no-password", "--no-owner", "--no-privileges", "-Fc",
                "-f", dump.toString(), database.getPath().substring(1));
            builder.environment().put("PGPASSWORD", required("BACKUP_DB_PASSWORD"));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            try {
                if (!process.waitFor(30, TimeUnit.MINUTES) || process.exitValue() != 0) throw new IOException("pg_dump falló");
            } finally {
                if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
            }
            create(dump, Path.of(System.getenv().getOrDefault("SIGAA_UPLOADS_DIR", "/app/uploads")), output, key);
            Path success = output.toAbsolutePath().getParent().resolve("last-success");
            Path staged = Files.createTempFile(success.getParent(), ".success-", ".tmp");
            try {
                Files.writeString(staged, Long.toString(Instant.now().getEpochSecond()), StandardCharsets.US_ASCII);
                Files.move(staged, success, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(staged); }
        } finally { Files.deleteIfExists(dump); }
    }

    public static void create(Path dump, Path uploads, Path output, byte[] key) throws Exception {
        validateDump(dump);
        if (!Files.isDirectory(uploads, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Falta uploads");
        Path destination = output.toAbsolutePath().normalize();
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException("La copia ya existe");
        Files.createDirectories(destination.getParent());
        Path staged = Files.createTempFile(destination.getParent(), ".sigaa-full-", ".tmp");
        try {
            Properties manifest = new Properties();
            manifest.setProperty("format", "sigaa-full-1");
            manifest.setProperty("createdAt", Instant.now().toString());
            manifest.setProperty("release", System.getenv().getOrDefault("SIGAA_RELEASE_ID", "unspecified"));
            try (var encrypted = encrypt(staged, key); var zip = new ZipOutputStream(encrypted)) {
                addFile(zip, dump, "database.dump", manifest);
                try (var files = Files.walk(uploads)) {
                    for (Path file : files.sorted().toList()) {
                        if (Files.isSymbolicLink(file)) throw new IOException("No se admiten enlaces en uploads");
                        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
                        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archivo no regular");
                        String name = "uploads/" + uploads.relativize(file).toString().replace('\\', '/');
                        addFile(zip, file, name, manifest);
                    }
                }
                zip.putNextEntry(new ZipEntry("manifest.properties"));
                manifest.store(zip, "SIGAA full backup; no secrets"); zip.closeEntry();
            }
            // La copia se publica solo al terminar y autenticar todo el cifrado.
            moveNew(staged, destination);
        } finally { Files.deleteIfExists(staged); }
    }

    private static void addFile(ZipOutputStream zip, Path file, String name, Properties manifest) throws Exception {
        validateName(name);
        long size = Files.size(file);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        zip.putNextEntry(new ZipEntry(name));
        long copied;
        try (var input = new DigestInputStream(Files.newInputStream(file), digest)) { copied = input.transferTo(zip); }
        zip.closeEntry();
        if (copied != size || Files.size(file) != size) throw new IOException("Archivo modificado durante el respaldo");
        manifest.setProperty("sha256." + name, HexFormat.of().formatHex(digest.digest()));
    }

    public static void extract(Path encrypted, Path output, byte[] key) throws Exception {
        Path destination = output.toAbsolutePath().normalize();
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException("El destino debe ser nuevo");
        Files.createDirectories(destination.getParent());
        Path working = Files.createTempDirectory(destination.getParent(), ".sigaa-restore-");
        privateDirectory(working);
        try {
            Path zipFile = working.resolve("payload.zip");
            decrypt(encrypted, zipFile, key, FULL_MAGIC); // Verificar GCM ANTES de extraer o publicar archivos.
            Path restored = Files.createDirectory(working.resolve("restored"));
            privateDirectory(restored);
            Properties manifest = new Properties(); Map<String, String> hashes = new HashMap<>();
            Set<String> names = new HashSet<>(); long total = 0;
            try (var zip = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName(); validateName(name);
                    if (entry.isDirectory() || !names.add(name) || names.size() > 1_000_000) throw new IOException("Entrada duplicada o inválida");
                    Path target = restored.resolve(name).normalize();
                    if (!target.startsWith(restored)) throw new IOException("Ruta fuera del respaldo");
                    Files.createDirectories(target.getParent());
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (var out = new DigestOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW), digest)) {
                        byte[] buffer = new byte[8192]; int read; long fileSize = 0;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read; fileSize += read;
                            if (total > MAX_EXTRACT_BYTES || (name.equals("manifest.properties") && fileSize > 16 * 1024 * 1024))
                                throw new IOException("Respaldo demasiado grande");
                            out.write(buffer, 0, read);
                        }
                    }
                    if (!name.equals("manifest.properties")) hashes.put(name, HexFormat.of().formatHex(digest.digest()));
                }
            }
            try (var input = Files.newInputStream(restored.resolve("manifest.properties"))) { manifest.load(input); }
            if (!"sigaa-full-1".equals(manifest.getProperty("format")) || !hashes.containsKey("database.dump")) throw new IOException("Manifiesto inválido");
            long expected = manifest.stringPropertyNames().stream().filter(name -> name.startsWith("sha256.")).count();
            if (expected != hashes.size()) throw new IOException("Faltan archivos");
            for (var entry : hashes.entrySet()) if (!entry.getValue().equals(manifest.getProperty("sha256." + entry.getKey())))
                throw new IOException("Checksum inválido");
            validateDump(restored.resolve("database.dump"));
            Files.createDirectories(restored.resolve("uploads"));
            moveNew(restored, destination);
        } finally { deleteWorking(working); }
    }

    public static void decryptDatabase(Path encrypted, Path output, byte[] key) throws Exception {
        Path destination = output.toAbsolutePath().normalize();
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException("El destino debe ser nuevo");
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".sigaa-db-", ".tmp");
        try { decrypt(encrypted, temporary, key, DB_MAGIC); validateDump(temporary); moveNew(temporary, destination); }
        finally { Files.deleteIfExists(temporary); }
    }

    private static Cipher cipher(byte[] key, byte[] iv, int mode, byte[] magic) throws GeneralSecurityException {
        if (key.length != 32) throw new InvalidKeyException("Se requieren 32 bytes");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        if (Arrays.equals(magic, FULL_MAGIC)) cipher.updateAAD(magic);
        return cipher;
    }
    private static OutputStream encrypt(Path file, byte[] key) throws Exception {
        return new FramedOutput(file, key);
    }
    private static void decrypt(Path input, Path output, byte[] key, byte[] magic) throws Exception {
        try (var raw = new BufferedInputStream(Files.newInputStream(input))) {
            if (!Arrays.equals(raw.readNBytes(magic.length), magic)) throw new IOException("Formato incorrecto");
            byte[] iv = raw.readNBytes(12); if (iv.length != 12) throw new EOFException();
            if (Arrays.equals(magic, FULL_MAGIC)) {
                var data = new DataInputStream(raw); long sequence = 0; long total = 0;
                try (var target = Files.newOutputStream(output)) {
                    while (true) {
                        int size = data.readInt();
                        if (size < 0 || size > FRAME_BYTES || (total += size) > MAX_EXTRACT_BYTES) throw new IOException("Bloque inválido");
                        byte[] encrypted = data.readNBytes(size + 16);
                        if (encrypted.length != size + 16) throw new EOFException();
                        byte[] plain = frameCipher(key, iv, sequence++, size, Cipher.DECRYPT_MODE).doFinal(encrypted);
                        if (size == 0) {
                            if (data.read() != -1) throw new IOException("Datos posteriores al bloque final");
                            break;
                        }
                        target.write(plain);
                    }
                }
                return;
            }
            try (var decrypted = new CipherInputStream(raw, cipher(key, iv, Cipher.DECRYPT_MODE, magic));
                 var target = Files.newOutputStream(output)) { decrypted.transferTo(target); }
        }
    }
    private static Cipher frameCipher(byte[] key, byte[] prefix, long sequence, int size, int mode) throws GeneralSecurityException {
        if (sequence < 0 || key.length != 32) throw new InvalidKeyException();
        byte[] nonce = java.nio.ByteBuffer.allocate(20).put(prefix).putLong(sequence).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(java.nio.ByteBuffer.allocate(FULL_MAGIC.length + 12).put(FULL_MAGIC).putLong(sequence).putInt(size).array());
        return cipher;
    }
    /** Bloques AEAD acotados: GCM de un único archivo grande agotaría la RAM al descifrar. */
    private static final class FramedOutput extends OutputStream {
        private final DataOutputStream output;
        private final byte[] key, prefix = new byte[12], buffer = new byte[FRAME_BYTES];
        private int used; private long sequence; private boolean closed;
        FramedOutput(Path path, byte[] key) throws Exception {
            if (key.length != 32) throw new InvalidKeyException();
            this.key = key.clone(); new SecureRandom().nextBytes(prefix);
            output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
            output.write(FULL_MAGIC); output.write(prefix);
        }
        @Override public void write(int value) throws IOException { write(new byte[] {(byte) value}, 0, 1); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            if (closed) throw new IOException("Flujo cerrado");
            Objects.checkFromIndexSize(offset, length, bytes.length);
            while (length > 0) {
                int count = Math.min(length, FRAME_BYTES - used);
                System.arraycopy(bytes, offset, buffer, used, count);
                used += count; offset += count; length -= count;
                if (used == FRAME_BYTES) frame();
            }
        }
        private void frame() throws IOException {
            try {
                byte[] encrypted = frameCipher(key, prefix, sequence++, used, Cipher.ENCRYPT_MODE).doFinal(buffer, 0, used);
                output.writeInt(used); output.write(encrypted); used = 0;
            } catch (GeneralSecurityException ex) { throw new IOException("No se pudo cifrar el bloque", ex); }
        }
        @Override public void close() throws IOException {
            if (closed) return;
            try { if (used > 0) frame(); frame(); }
            finally { closed = true; Arrays.fill(key, (byte) 0); output.close(); }
        }
    }
    private static void validateDump(Path dump) throws IOException {
        try (var input = Files.newInputStream(dump)) {
            if (!Arrays.equals(input.readNBytes(5), "PGDMP".getBytes(StandardCharsets.US_ASCII))) throw new IOException("Dump inválido");
        }
    }
    private static void validateName(String name) throws IOException {
        if (name.contains("\\") || name.startsWith("/") || name.contains(":") || Arrays.asList(name.split("/", -1)).contains("..")
            || !(name.equals("database.dump") || name.equals("manifest.properties") || name.startsWith("uploads/"))) throw new IOException("Ruta inválida");
    }
    private static void privateDirectory(Path directory) throws IOException {
        try { Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { /* ACL heredada en Windows. */ }
    }
    private static void moveNew(Path source, Path destination) throws IOException {
        // Sin REPLACE_EXISTING: una recuperación nunca sobrescribe el destino.
        Files.move(source, destination);
    }
    private static void deleteWorking(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
