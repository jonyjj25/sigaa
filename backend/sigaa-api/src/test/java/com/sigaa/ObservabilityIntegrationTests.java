package com.sigaa;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.AuthenticationFailedException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
class ObservabilityIntegrationTests {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private MeterRegistry meterRegistry;

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @MockitoBean(name = "mailSender")
    private JavaMailSenderImpl mailSender;

    @BeforeEach
    void unavailableSmtp() throws Exception {
        doThrow(new AuthenticationFailedException("SMTP unavailable in test"))
                .when(mailSender).testConnection();
    }

    @Test
    void readinessDependenciesAreHealthy() {
        assertEquals(Status.UP, healthEndpoint.healthForPath("readiness").getStatus());
    }

    @Test
    void smtpFailureRemainsVisibleAs503OnManagementHealth() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertEquals(503, response.statusCode());
        assertEquals(Status.DOWN, healthEndpoint.health().getStatus());
        assertFalse(response.body().contains("components"));
        assertFalse(response.body().contains("SMTP unavailable"));
        verify(mailSender, atLeastOnce()).testConnection();
    }

    @Test
    void probesStayHealthyOnBothPortsWhenSmtpFails() throws Exception {
        for (String path : new String[]{"/livez", "/readyz"}) {
            HttpResponse<String> response = get(applicationPort, path);
            assertEquals(200, response.statusCode(), path);
            assertEquals("{\"status\":\"UP\"}", response.body(), path);
        }
        for (String path : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            HttpResponse<String> response = get(managementPort, path);
            assertEquals(200, response.statusCode(), path);
            assertEquals("{\"status\":\"UP\"}", response.body(), path);
        }
    }

    @Test
    void healthIsNotPublishedOnApplicationPort() throws Exception {
        HttpResponse<String> response = get(applicationPort, "/actuator/health");

        assertEquals(404, response.statusCode());
        assertEquals("{\"error\":\"Recurso no encontrado\"}", response.body());
    }

    @Test
    void aggregateRecoversWhenSmtpConnectionSucceeds() throws Exception {
        doNothing().when(mailSender).testConnection();

        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertEquals(200, response.statusCode());
        assertEquals(Status.UP, healthEndpoint.health().getStatus());
        verify(mailSender, atLeastOnce()).testConnection();
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void operationalBackupMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("sigaa_backup_total").tag("result", "success").counter());
        assertNotNull(meterRegistry.find("sigaa_backup_total").tag("result", "failure").counter());
        assertNotNull(meterRegistry.find("sigaa_restore_test_total").tag("result", "success").counter());
        assertNotNull(meterRegistry.find("sigaa_backup_last_success_epoch_seconds").gauge());
    }
}
