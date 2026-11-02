package com.sigaa.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Datos editados aún sin guardar; la identidad y el firmante los resuelve el servidor. */
public record NotaVistaPreviaRequest(Long notaId, Long numeroId, Long institucionId, LocalDate fechaOficial,
    @Size(max = 200) String asunto, @Size(max = 200) String destinatarioNombre,
    @Size(max = 200) String destinatarioCargo, @Size(max = 15000) String contenido, Boolean institucionDefinida) {
    public NotaVistaPreviaRequest(Long notaId, Long numeroId, Long institucionId, LocalDate fechaOficial,
                                 String asunto, String destinatarioNombre, String destinatarioCargo, String contenido) {
        this(notaId, numeroId, institucionId, fechaOficial, asunto, destinatarioNombre, destinatarioCargo, contenido, false);
    }
}
