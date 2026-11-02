package com.sigaa.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotaReservaScheduler {
    private final NotaNumeroService numeroService;

    public NotaReservaScheduler(NotaNumeroService numeroService) { this.numeroService = numeroService; }

    @Scheduled(fixedDelayString = "${app.notas.reservation-cleanup-ms:60000}")
    public void liberarReservasExpiradas() { numeroService.liberarExpiradas(); }
}
