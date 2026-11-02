package com.sigaa;

import com.sigaa.config.RuntimeDatabasePrivilegeGuard;
import jakarta.mail.AuthenticationFailedException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

@EnabledIfSystemProperty(named = "sigaa.security.validation", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles({"test", "prod"})
class ProductionProfileSecurityIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired RuntimeDatabasePrivilegeGuard privilegeGuard;
    @Autowired HealthEndpoint health;
    @MockitoBean(name = "mailSender") JavaMailSenderImpl mailSender;

    @Test void productionStartsWithRuntimePrivilegesAndKeepsSmtpFailureVisible() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT current_user")) {
            assertTrue(result.next()); assertEquals("sigaa_runtime", result.getString(1));
        }
        privilegeGuard.afterSingletonsInstantiated();
        doThrow(new AuthenticationFailedException("SMTP ficticio sin configurar")).when(mailSender).testConnection();
        assertEquals(Status.UP, health.healthForPath("liveness").getStatus());
        assertEquals(Status.UP, health.healthForPath("readiness").getStatus());
        assertEquals(Status.DOWN, health.health().getStatus());
    }
}
