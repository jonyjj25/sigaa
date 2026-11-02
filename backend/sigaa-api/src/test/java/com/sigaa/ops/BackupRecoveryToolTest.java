package com.sigaa.ops;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class BackupRecoveryToolTest {
    @TempDir Path root;
    private final byte[] key = new byte[32];

    @Test void restoresDatabaseAndBinaryAttachmentsAcrossAuthenticatedFrames() throws Exception {
        Path dump = dump(), uploads = Files.createDirectory(root.resolve("uploads"));
        byte[] large = new byte[3 * 1024 * 1024]; new SecureRandom().nextBytes(large);
        Path nested = Files.createDirectories(uploads.resolve("tareas/subcarpeta"));
        Files.write(nested.resolve("acta-ñ.pdf"), large);
        Path encrypted = root.resolve("full.enc"), restored = root.resolve("restored");
        BackupRecoveryTool.create(dump, uploads, encrypted, key);
        BackupRecoveryTool.extract(encrypted, restored, key);
        assertThat(Files.readAllBytes(restored.resolve("database.dump"))).isEqualTo(Files.readAllBytes(dump));
        assertThat(Files.readAllBytes(restored.resolve("uploads/tareas/subcarpeta/acta-ñ.pdf"))).isEqualTo(large);
        Properties manifest = new Properties();
        try (var input = Files.newInputStream(restored.resolve("manifest.properties"))) { manifest.load(input); }
        assertThat(manifest.getProperty("sha256.uploads/tareas/subcarpeta/acta-ñ.pdf")).hasSize(64);
    }

    @Test void corruptionWrongKeyAndTruncationNeverPublishPartialRestores() throws Exception {
        Path encrypted = root.resolve("full.enc");
        BackupRecoveryTool.create(dump(), Files.createDirectory(root.resolve("uploads")), encrypted, key);
        byte[] original = Files.readAllBytes(encrypted), wrongKey = new byte[32]; wrongKey[0] = 1;
        assertThatThrownBy(() -> BackupRecoveryTool.extract(encrypted, root.resolve("wrong"), wrongKey)).isInstanceOf(Exception.class);
        byte[] corrupt = original.clone(); corrupt[corrupt.length - 1] ^= 1;
        Files.write(encrypted, corrupt);
        assertThatThrownBy(() -> BackupRecoveryTool.extract(encrypted, root.resolve("corrupt"), key)).isInstanceOf(Exception.class);
        Files.write(encrypted, Arrays.copyOf(original, original.length - 20));
        assertThatThrownBy(() -> BackupRecoveryTool.extract(encrypted, root.resolve("truncated"), key)).isInstanceOf(Exception.class);
        assertThat(root.resolve("wrong")).doesNotExist(); assertThat(root.resolve("corrupt")).doesNotExist();
        assertThat(root.resolve("truncated")).doesNotExist();
        try (var files = Files.list(root)) { assertThat(files.noneMatch(path -> path.getFileName().toString().startsWith(".sigaa-restore-"))).isTrue(); }
    }

    @Test void refusesToOverwriteAnExistingBackupOrRestoreDirectory() throws Exception {
        Path dump = dump(), uploads = Files.createDirectory(root.resolve("uploads")), encrypted = root.resolve("full.enc");
        BackupRecoveryTool.create(dump, uploads, encrypted, key);
        byte[] original = Files.readAllBytes(encrypted);
        assertThatThrownBy(() -> BackupRecoveryTool.create(dump, uploads, encrypted, key)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readAllBytes(encrypted)).isEqualTo(original);
        assertThatThrownBy(() -> BackupRecoveryTool.extract(encrypted, uploads, key)).isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test void authenticatedZipTraversalIsRejectedWithoutEscapingTheRestoreDirectory() throws Exception {
        Path encrypted = root.resolve("malicious.enc");
        var encrypt = BackupRecoveryTool.class.getDeclaredMethod("encrypt", Path.class, byte[].class);
        encrypt.setAccessible(true);
        try (var zip = new ZipOutputStream((OutputStream) encrypt.invoke(null, encrypted, key))) {
            zip.putNextEntry(new ZipEntry("uploads/../../escaped.txt")); zip.write("FICTICIO".getBytes()); zip.closeEntry();
        }
        assertThatThrownBy(() -> BackupRecoveryTool.extract(encrypted, root.resolve("restored"), key)).isInstanceOf(IOException.class);
        assertThat(root.resolve("escaped.txt")).doesNotExist(); assertThat(root.resolve("restored")).doesNotExist();
    }

    @Test void decryptsLegacyDatabaseOnlyBackupsWithoutChangingThem() throws Exception {
        Path encrypted = root.resolve("legacy.enc"), restored = root.resolve("database.dump");
        byte[] original = "PGDMP_FICTITIOUS".getBytes(StandardCharsets.US_ASCII), iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        try (var raw = Files.newOutputStream(encrypted)) {
            raw.write("SIGAABK1".getBytes(StandardCharsets.US_ASCII)); raw.write(iv);
            try (var output = new CipherOutputStream(raw, cipher)) { output.write(original); }
        }
        BackupRecoveryTool.decryptDatabase(encrypted, restored, key);
        assertThat(Files.readAllBytes(restored)).isEqualTo(original);
    }

    private Path dump() throws IOException { return Files.write(root.resolve("source.dump"), "PGDMP_FICTITIOUS_TEST_ONLY".getBytes(StandardCharsets.US_ASCII)); }
}
