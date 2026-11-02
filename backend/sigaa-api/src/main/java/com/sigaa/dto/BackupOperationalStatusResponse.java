package com.sigaa.dto;

import java.time.LocalDateTime;

public record BackupOperationalStatusResponse(
        boolean listoParaRespaldoManual,
        boolean cifradoConfigurado,
        boolean pgDumpDisponible,
        boolean pgRestoreDisponible,
        boolean copiaExternaConfigurada,
        boolean respaldoAutomaticoActivo,
        boolean pruebaRestauracionActiva,
        boolean respaldoEnProgreso,
        int retencionDias,
        int retencionCopias,
        String horarioRespaldo,
        String horarioPruebaRestauracion,
        LocalDateTime ultimoRespaldoExitoso,
        LocalDateTime ultimaRestauracionVerificada,
        long totalRespaldos) {
}
