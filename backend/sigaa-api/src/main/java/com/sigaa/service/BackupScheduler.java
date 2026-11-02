package com.sigaa.service;

import com.sigaa.util.LogDiagnostics;
import com.sigaa.model.Backup;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler {
    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private final BackupService backupService;
    private final boolean automaticEnabled;
    private final boolean restoreTestEnabled;

    public BackupScheduler(BackupService backupService,
            @Value("${app.backup.automatic-enabled:false}") boolean automaticEnabled,
            @Value("${app.backup.restore-test-enabled:false}") boolean restoreTestEnabled) {
        this.backupService = backupService;
        this.automaticEnabled = automaticEnabled;
        this.restoreTestEnabled = restoreTestEnabled;
    }

    @Scheduled(cron = "${app.backup.schedule-cron:0 0 2 * * *}", zone = "${app.time-zone:America/Panama}")
    public void createAutomaticBackup() {
        if (!automaticEnabled) return;
        try {
            backupService.create("automatico", "system-scheduler");
        } catch (RuntimeException ex) {
            log.error("Falló el respaldo automático: {}", LogDiagnostics.describe(ex));
        }
    }

    @Scheduled(cron = "${app.backup.restore-test-cron:0 0 4 * * SUN}", zone = "${app.time-zone:America/Panama}")
    public void verifyLatestBackup() {
        if (!restoreTestEnabled) return;
        try {
            List<Backup> backups = backupService.list().stream()
                    .filter(backup -> "completado".equalsIgnoreCase(backup.getEstado())).toList();
            if (backups.isEmpty()) {
                log.warn("No hay respaldos disponibles para probar restauración");
                return;
            }
            backupService.testRestore(backups.getFirst().getId(), "system-restore-test");
        } catch (RuntimeException ex) {
            log.error("Falló la prueba periódica de restauración: {}", LogDiagnostics.describe(ex));
        }
    }
}
