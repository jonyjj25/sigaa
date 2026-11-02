package com.sigaa.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.monitoring.smtp-probe-enabled", havingValue = "true")
public class SmtpHealthMetrics {
    private final HealthEndpoint health;
    private final AtomicInteger available = new AtomicInteger(-1);
    public SmtpHealthMetrics(HealthEndpoint health, MeterRegistry registry) {
        this.health = health;
        Gauge.builder("sigaa_smtp_available", available, AtomicInteger::get).register(registry);
    }
    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void probe() {
        try { var mail = health.healthForPath("mail"); available.set(mail != null && Status.UP.equals(mail.getStatus()) ? 1 : 0); }
        catch (RuntimeException exception) { available.set(0); }
    }
}
