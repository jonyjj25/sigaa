package com.sigaa.service;

import com.sigaa.model.Backup;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class BackupSchedulerTest {
    @Test void restoreScheduleSkipsTheNewestMissingBackupAndUsesTheLatestAvailableOne() {
        BackupService service = mock(BackupService.class);
        when(service.list()).thenReturn(List.of(backup(3L, "fallido"), backup(2L, "en_progreso"), backup(1L, "completado")));
        new BackupScheduler(service, false, true).verifyLatestBackup();
        verify(service).testRestore(1L, "system-restore-test");
        verify(service, never()).testRestore(3L, "system-restore-test");
    }
    @Test void restoreScheduleDoesNotStartAnUnavailableBackup() {
        BackupService service = mock(BackupService.class);
        when(service.list()).thenReturn(List.of(backup(1L, "fallido")));
        new BackupScheduler(service, false, true).verifyLatestBackup();
        verify(service, never()).testRestore(anyLong(), anyString());
    }
    @Test void disabledSchedulesRemainInactive() {
        BackupService service = mock(BackupService.class);
        BackupScheduler scheduler = new BackupScheduler(service, false, false);
        scheduler.createAutomaticBackup(); scheduler.verifyLatestBackup();
        verifyNoInteractions(service);
    }
    @Test void automaticBackupKeepsItsExistingTypeAndActor() {
        BackupService service = mock(BackupService.class);
        new BackupScheduler(service, true, false).createAutomaticBackup();
        verify(service).create("automatico", "system-scheduler");
    }
    private Backup backup(Long id, String state) { Backup b = new Backup(); b.setId(id); b.setEstado(state); return b; }
}
