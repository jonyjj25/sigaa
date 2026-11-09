package com.sigaa.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigaa.config.SecurityConfig;
import com.sigaa.model.Horario;
import com.sigaa.model.Usuario;
import com.sigaa.repository.HorarioRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.security.JwtFilter;
import com.sigaa.security.JwtUtil;
import com.sigaa.service.SecurityIpService;
import com.sigaa.service.SecurityPolicyService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Seguridad HTTP y contrato JSON con repositorios ficticios, sin base de datos ni credenciales. */
@SpringJUnitWebConfig(HorarioResponseSecurityTest.Config.class)
class HorarioResponseSecurityTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement
    @Import({SecurityConfig.class, HorarioController.class})
    static class Config {
        @Bean HorarioRepository schedules() { return mock(HorarioRepository.class); }
        @Bean UsuarioRepository users() { return mock(UsuarioRepository.class); }
        @Bean SecurityPolicyService policy() { return mock(SecurityPolicyService.class); }
        @Bean UserDetailsService details() { return mock(UserDetailsService.class); }
        @Bean JwtFilter filter(UsuarioRepository users, UserDetailsService details) {
            return new JwtFilter(mock(JwtUtil.class), details, mock(SecurityIpService.class), users, mock(SecurityPolicyService.class));
        }
        @Bean PlatformTransactionManager transactionManager() {
            // Solo activa el contexto transaccional de Spring; no abre conexiones.
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
                @Override protected void doCommit(DefaultTransactionStatus status) {}
                @Override protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }

    private static final Set<String> HORARIO_FIELDS = Set.of(
            "id", "usuario", "dia", "entrada", "salida", "tolerancia", "estado", "fechaInicio", "fechaFin");
    private static final Set<String> USUARIO_FIELDS = Set.of("id", "nombre", "apellido");
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "password", "passwordHash", "hash", "contrasena", "contrasenaTemporal", "token", "tokenVersion",
            "accessToken", "refreshToken", "recoveryCode", "codigoRecuperacion", "roles", "permisos", "membresias",
            "intentosFallidos", "bloqueadoHasta", "ultimoAcceso", "fechaExpiracion", "contrasenaTemporalExpiraEn",
            "cambioContrasenaRequerido");
    @Autowired WebApplicationContext context;
    @Autowired HorarioRepository schedules;
    @Autowired UsuarioRepository users;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Usuario assignedUser;
    private Horario schedule;

    @BeforeEach void setup() {
        reset(new Object[] {schedules, users});
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // Cualquier acceso a otro getter falla sin crear ni imprimir valores sensibles.
        assignedUser = mock(Usuario.class, invocation -> {
            return switch (invocation.getMethod().getName()) {
                case "getId" -> 7L;
                case "getNombre" -> "Usuario de prueba";
                case "getApellido" -> "Ficticio";
                default -> {
                    if (invocation.getMethod().getName().startsWith("get")
                            || invocation.getMethod().getName().startsWith("is")) {
                        throw new AssertionError("Horarios no debe leer " + invocation.getMethod().getName());
                    }
                    yield RETURNS_DEFAULTS.answer(invocation);
                }
            };
        });
        schedule = new Horario();
        schedule.setId(11L);
        schedule.setUsuario(assignedUser);
        schedule.setDia(3);
        schedule.setFechaInicio(LocalDate.of(2026, 1, 1));
        schedule.setFechaFin(LocalDate.of(2026, 12, 31));
    }

    @ParameterizedTest @ValueSource(strings = {"/api/horarios", "/api/horarios/usuario/7"})
    void authorizedListsContainOnlyTheScheduleAndMinimalUser(String endpoint) throws Exception {
        when(schedules.findAll()).thenReturn(List.of(schedule));
        when(schedules.findByUsuarioIdOrderByDiaAsc(7L)).thenReturn(List.of(schedule));
        String body = mvc.perform(admin(get(endpoint))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertScheduleJson(json.readTree(body).get(0));
        if (endpoint.endsWith("/7")) verify(schedules).findByUsuarioIdOrderByDiaAsc(7L);
        else verify(schedules).findAll();
    }

    @Test void createKeepsAssignmentAndReturnsOnlyTheAllowedFields() throws Exception {
        when(users.findById(7L)).thenReturn(Optional.of(assignedUser));
        when(schedules.save(any(Horario.class))).thenAnswer(invocation -> {
            Horario saved = invocation.getArgument(0);
            assertSame(assignedUser, saved.getUsuario());
            assertEquals(3, saved.getDia());
            assertEquals("09:00", saved.getEntrada());
            assertEquals("18:00", saved.getSalida());
            assertEquals(5, saved.getTolerancia());
            assertEquals("especial", saved.getEstado());
            saved.setId(11L);
            return saved;
        });
        String body = mvc.perform(admin(post("/api/horarios")).contentType("application/json")
                        .content("{\"usuarioId\":7,\"dia\":3,\"entrada\":\"09:00\",\"salida\":\"18:00\",\"tolerancia\":5,\"estado\":\"especial\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertScheduleJson(json.readTree(body));
        assertEquals("09:00", json.readTree(body).get("entrada").asText());
    }

    @Test void createPreservesCurrentDefaults() throws Exception {
        when(users.findById(7L)).thenReturn(Optional.of(assignedUser));
        when(schedules.save(any(Horario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(admin(post("/api/horarios")).contentType("application/json").content("{\"usuarioId\":7}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dia").value(1))
                .andExpect(jsonPath("$.entrada").value("08:00")).andExpect(jsonPath("$.salida").value("17:00"))
                .andExpect(jsonPath("$.tolerancia").value(15)).andExpect(jsonPath("$.estado").value("normal"));
    }

    @Test void updateKeepsAssignmentAndDatesAndReturnsTheSameSafeShape() throws Exception {
        when(schedules.findById(11L)).thenReturn(Optional.of(schedule));
        when(schedules.save(schedule)).thenReturn(schedule);
        String body = mvc.perform(admin(put("/api/horarios/11")).contentType("application/json")
                        .content("{\"dia\":4,\"entrada\":\"10:00\",\"salida\":\"19:00\",\"tolerancia\":8,\"estado\":\"especial\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fechaInicio").value("2026-01-01"))
                .andExpect(jsonPath("$.fechaFin").value("2026-12-31"))
                .andReturn().getResponse().getContentAsString();
        assertScheduleJson(json.readTree(body));
        assertSame(assignedUser, schedule.getUsuario());
        assertEquals(4, schedule.getDia());
        assertEquals("10:00", schedule.getEntrada());
        assertEquals("19:00", schedule.getSalida());
        assertEquals(8, schedule.getTolerancia());
        assertEquals("especial", schedule.getEstado());
        verify(schedules).save(schedule);
        verifyNoInteractions(users);
    }

    @Test void partialUpdatePreservesUnspecifiedFields() throws Exception {
        when(schedules.findById(11L)).thenReturn(Optional.of(schedule));
        when(schedules.save(schedule)).thenReturn(schedule);
        mvc.perform(admin(put("/api/horarios/11")).contentType("application/json").content("{\"tolerancia\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dia").value(3))
                .andExpect(jsonPath("$.entrada").value("08:00")).andExpect(jsonPath("$.salida").value("17:00"))
                .andExpect(jsonPath("$.tolerancia").value(0));
    }

    @Test void mappingReadsLazyUserDataInsideTheTransaction() throws Exception {
        Usuario lazyUser = mock(Usuario.class);
        when(lazyUser.getId()).thenReturn(7L);
        when(lazyUser.getNombre()).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return "Usuario de prueba";
        });
        when(lazyUser.getApellido()).thenReturn("Ficticio");
        schedule.setUsuario(lazyUser);
        when(schedules.findAll()).thenReturn(List.of(schedule));
        mvc.perform(admin(get("/api/horarios"))).andExpect(status().isOk());
        verify(lazyUser).getNombre();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test void nullableUserDoesNotBreakTheList() throws Exception {
        schedule.setUsuario(null);
        when(schedules.findAll()).thenReturn(List.of(schedule));
        String body = mvc.perform(admin(get("/api/horarios"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode item = json.readTree(body).get(0);
        assertEquals(HORARIO_FIELDS, fields(item));
        assertTrue(item.get("usuario").isNull());
        assertNoForbiddenFields(item);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/horarios", "/api/horarios/usuario/7"})
    void emptyListsRemainArrays(String endpoint) throws Exception {
        mvc.perform(admin(get(endpoint))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"usuarioId\":7,\"dia\":8}",
            "{\"usuarioId\":7,\"entrada\":\"99:99\"}", "{\"usuarioId\":7,\"tolerancia\":-1}"})
    void invalidCreateStillReturnsBadRequestWithoutSaving(String requestBody) throws Exception {
        mvc.perform(admin(post("/api/horarios")).contentType("application/json").content(requestBody))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules, users);
    }

    @Test void unknownUserStillCannotBeAssigned() throws Exception {
        mvc.perform(admin(post("/api/horarios")).contentType("application/json").content("{\"usuarioId\":7}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules);
    }

    @Test void invalidUpdateStillReturnsBadRequestWithoutSaving() throws Exception {
        mvc.perform(admin(put("/api/horarios/11")).contentType("application/json").content("{\"salida\":\"99:99\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules);
    }

    @Test void missingScheduleStillReturnsNotFound() throws Exception {
        mvc.perform(admin(put("/api/horarios/11")).contentType("application/json").content("{\"dia\":3}"))
                .andExpect(status().isNotFound());
        verify(schedules, never()).save(any());
    }

    @Test void administratorCanStillDeleteWithoutAResponseBody() throws Exception {
        when(schedules.existsById(11L)).thenReturn(true);
        mvc.perform(admin(delete("/api/horarios/11"))).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(schedules).deleteById(11L);
    }

    @ParameterizedTest @ValueSource(strings = {"list", "user", "create", "update", "delete"})
    void anonymousCannotAccessAnyScheduleOperation(String operation) throws Exception {
        mvc.perform(request(operation)).andExpect(status().isUnauthorized());
        verifyNoInteractions(schedules, users);
    }

    @ParameterizedTest @ValueSource(strings = {"list", "user", "create", "update", "delete"})
    void nonAdministratorCannotAccessAnyScheduleOperation(String operation) throws Exception {
        mvc.perform(request(operation).with(user("usuario@example.test").roles("miembro")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(schedules, users);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(user("admin@example.test").roles("administrador"));
    }

    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "list" -> get("/api/horarios");
            case "user" -> get("/api/horarios/usuario/7");
            case "create" -> post("/api/horarios").contentType("application/json").content("{\"usuarioId\":7}");
            case "update" -> put("/api/horarios/11").contentType("application/json").content("{\"dia\":3}");
            case "delete" -> delete("/api/horarios/11");
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private void assertScheduleJson(JsonNode item) {
        assertEquals(HORARIO_FIELDS, fields(item));
        assertEquals(USUARIO_FIELDS, fields(item.get("usuario")));
        assertEquals(7L, item.get("usuario").get("id").asLong());
        assertEquals("Usuario de prueba", item.get("usuario").get("nombre").asText());
        assertEquals("Ficticio", item.get("usuario").get("apellido").asText());
        assertNoForbiddenFields(item);
    }

    private Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private void assertNoForbiddenFields(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                assertFalse(FORBIDDEN_FIELDS.contains(entry.getKey()), "Campo no permitido: " + entry.getKey());
                assertNoForbiddenFields(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::assertNoForbiddenFields);
        }
    }
}
