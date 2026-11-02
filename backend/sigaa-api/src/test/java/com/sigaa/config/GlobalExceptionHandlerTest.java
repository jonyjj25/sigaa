package com.sigaa.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(MvcConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void healthOnApplicationPortWithoutActuatorMappingReturns404() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(result -> assertInstanceOf(NoResourceFoundException.class, result.getResolvedException()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
    }

    @Test
    void unmappedRouteWithoutResourceHandlerReturns404() throws Exception {
        MockMvc withoutResources = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        withoutResources.perform(get("/actuator/health"))
                .andExpect(result -> assertInstanceOf(NoHandlerFoundException.class, result.getResolvedException()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
    }

    @Test
    void actualApplicationFailureStillReturns500WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void internalFailureKeepsErrorDiagnosticWithoutPrintingExceptionPayload() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            var response = new GlobalExceptionHandler().handleGeneral(new RuntimeException(
                    "TEST_ONLY_PRIVATE_VALUE", new java.io.IOException("TEST_ONLY_PRIVATE_PATH")));

            assertEquals(500, response.getStatusCode().value());
            assertEquals(1, captured.list.size());
            ILoggingEvent event = captured.list.getFirst();
            assertEquals(Level.ERROR, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("java.io.IOException"));
            assertFalse(event.getFormattedMessage().contains("TEST_ONLY"));
            assertNull(event.getThrowableProxy());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void databaseConflictKeepsWarningWithoutPrintingSqlOrRecordValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            var response = new GlobalExceptionHandler().handleConflict(new DataIntegrityViolationException(
                    "TEST_ONLY_SQL", new java.sql.SQLException("TEST_ONLY_RECORD_VALUE")));

            assertEquals(409, response.getStatusCode().value());
            assertEquals(1, captured.list.size());
            ILoggingEvent event = captured.list.getFirst();
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("java.sql.SQLException"));
            assertFalse(event.getFormattedMessage().contains("TEST_ONLY"));
            assertNull(event.getThrowableProxy());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test void invalidIdDateBodyAndMissingParameterAre400WithoutInternalDetails() throws Exception {
        MockMvc validation = MockMvcBuilders.standaloneSetup(new InputController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String path : new String[]{"/input/id/not-a-number", "/input/date/2026-99-99", "/input/required"}) {
            validation.perform(get(path)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(jsonPath("$.exception").doesNotExist());
        }
        validation.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/input/body")
                .contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test void unsupportedMethodKeeps405AndAllowInsteadOfBecoming500() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/test/failure"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Allow", "GET"));
    }

    @Test void frameworkMediaAndResponseStatusErrorsKeepTheir4xxWithoutLeakingDetails() throws Exception {
        MockMvc validation = MockMvcBuilders.standaloneSetup(new InputController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        validation.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/input/body")
                .contentType("text/plain").content("TEST_ONLY"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.message").doesNotExist());
        validation.perform(get("/input/json").accept("image/png"))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.exception").doesNotExist());
        validation.perform(get("/input/gone")).andExpect(status().isGone())
                .andExpect(jsonPath("$.message").doesNotExist()).andExpect(jsonPath("$.trace").doesNotExist());
    }

    @TestComponent
    @RestController
    static class InputController {
        @GetMapping(value = "/input/json", produces = "application/json") java.util.Map<String, String> json() { return java.util.Map.of("ok", "true"); }
        @GetMapping("/input/gone") String gone() {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE, "TEST_ONLY_INTERNAL_DETAIL");
        }
        @GetMapping("/input/id/{id}") String id(@org.springframework.web.bind.annotation.PathVariable Long id) { return "ok"; }
        @GetMapping("/input/date/{date}") String date(@org.springframework.web.bind.annotation.PathVariable String date) {
            java.time.LocalDate.parse(date); return "ok";
        }
        @GetMapping("/input/required") String required(@org.springframework.web.bind.annotation.RequestParam String value) { return "ok"; }
        @org.springframework.web.bind.annotation.PostMapping("/input/body")
        String body(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body) { return "ok"; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration implements WebMvcConfigurer {
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        FailingController failingController() {
            return new FailingController();
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }
    }

    @TestComponent
    @RestController
    static class FailingController {
        @GetMapping("/test/failure")
        String fail() {
            throw new RuntimeException("Detalle interno de diagnóstico");
        }
    }
}
