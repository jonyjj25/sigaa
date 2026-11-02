package com.sigaa.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackupStorageMetrics {
    public BackupStorageMetrics(MeterRegistry registry, @Value("${app.backup.external-directory:}") String external) {
        Path marker = external.isBlank() ? null : Path.of(external).resolve("full/last-success");
        Gauge.builder("sigaa_full_backup_last_success_epoch_seconds", () -> readMarker(marker)).register(registry);
        Gauge.builder("sigaa_uploads_available", () -> Files.isDirectory(Path.of("uploads")) && Files.isReadable(Path.of("uploads")) ? 1 : 0).register(registry);
    }
    static double readMarker(Path marker) {
        try {
            if (marker == null || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) > 32) return 0;
            long value = Long.parseLong(Files.readString(marker).trim());
            return value > 0 && value <= java.time.Instant.now().getEpochSecond() ? value : 0;
        } catch (Exception ex) { return 0; }
    }
}
