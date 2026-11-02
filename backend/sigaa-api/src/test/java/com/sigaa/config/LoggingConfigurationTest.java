package com.sigaa.config;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {
    @Test
    void defaultConfigurationDisablesSqlAndParametersButKeepsOperationalLogs() {
        // Sin auto-configuración, base de datos, perfiles de despliegue ni archivos de entorno.
        try (var context = new SpringApplicationBuilder(LoggingOnlyConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=classpath:application.properties",
                        "--spring.config.import=", "--spring.profiles.active=logging-isolated",
                        "--spring.main.banner-mode=off")) {
            var environment = context.getEnvironment();
            assertEquals("false", environment.getProperty("spring.jpa.show-sql"));
            assertEquals("false", environment.getProperty("spring.jpa.properties.hibernate.format_sql"));
            assertEquals("false", environment.getProperty("spring.mvc.log-request-details"));
            for (String name : new String[]{"org.hibernate.SQL", "org.hibernate.orm.jdbc.bind",
                    "org.hibernate.orm.jdbc.extract", "org.hibernate.type.descriptor.sql"}) {
                var logger = LoggerFactory.getLogger(name);
                assertFalse(logger.isDebugEnabled(), name);
                assertFalse(logger.isTraceEnabled(), name);
            }
            var applicationLogger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
            assertTrue(applicationLogger.isWarnEnabled());
            assertTrue(applicationLogger.isErrorEnabled());
            assertTrue(LoggerFactory.getLogger("com.sigaa.Startup").isInfoEnabled());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LoggingOnlyConfiguration { }
}
