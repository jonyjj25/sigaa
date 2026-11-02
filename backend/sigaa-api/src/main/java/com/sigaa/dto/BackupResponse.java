package com.sigaa.dto;

import java.time.LocalDateTime;

public record BackupResponse(
        Long id,
        String nombre,
        String tamanio,
        String estado,
        String tipo,
        LocalDateTime fecha,
        boolean copiaExterna,
        String checksumSha256,
        String verificacionEstado,
        LocalDateTime verificadoAt) {
}
