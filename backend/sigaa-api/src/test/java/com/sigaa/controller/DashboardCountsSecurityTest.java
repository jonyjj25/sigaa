package com.sigaa.controller;

import com.sigaa.config.SecurityConfig;
import com.sigaa.model.Reunion;
import com.sigaa.model.Tarea;
import com.sigaa.repository.*;
import com.sigaa.security.JwtFilter;
import com.sigaa.security.JwtUtil;
import com.sigaa.service.*;
import java.util.List;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifica conteos y delegación de alcance con seguridad HTTP real y repositorios ficticios. */
@SpringJUnitWebConfig(DashboardCountsSecurityTest.Config.class)
class DashboardCountsSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, DashboardController.class})
    static class Config {
        @Bean SecurityPolicyService policy() { return mock(SecurityPolicyService.class); }
        @Bean UserDetailsService details() { return mock(UserDetailsService.class); }
        @Bean UsuarioRepository users() { return mock(UsuarioRepository.class); }
        @Bean JwtFilter filter(UsuarioRepository users, UserDetailsService details) {
            return new JwtFilter(mock(JwtUtil.class), details, mock(SecurityIpService.class), users, mock(SecurityPolicyService.class));
        }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean BackupRepository backups() { return mock(BackupRepository.class); }
        @Bean AuditLogRepository audits() { return mock(AuditLogRepository.class); }
        @Bean InventarioActivoRepository inventory() { return mock(InventarioActivoRepository.class); }
        @Bean TareaRepository tasks() { return mock(TareaRepository.class); }
        @Bean ReunionRepository meetings() { return mock(ReunionRepository.class); }
        @Bean TareaAccessService taskAccess() { return mock(TareaAccessService.class); }
        @Bean ReunionService meetingAccess() { return mock(ReunionService.class); }
    }
    @Autowired WebApplicationContext context;
    @Autowired TareaRepository tasks;
    @Autowired ReunionRepository meetings;
    @Autowired TareaAccessService taskAccess;
    @Autowired ReunionService meetingAccess;
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    private MockMvc mvc;
    @BeforeEach void setup() {
        reset(tasks, meetings, taskAccess, meetingAccess, users, roles);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void administratorTaskCountsUseOneAggregationWithTheSameStatuses() throws Exception {
        when(tasks.countByEstadoAgrupado()).thenReturn(List.of(new Object[]{"por_hacer", 2L}, new Object[]{"completada", 3L}, new Object[]{"otro", 9L}));
        mvc.perform(get("/api/dashboard/tareas-por-estado").with(user("admin@example.test").roles("administrador")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].value").value(2))
            .andExpect(jsonPath("$.items[1].value").value(0)).andExpect(jsonPath("$.items[2].value").value(3));
        verify(tasks).countByEstadoAgrupado(); verifyNoMoreInteractions(tasks); verifyNoInteractions(taskAccess);
    }

    @Test void administratorKpisPreserveValuesAndTrendsWithOneUserAggregation() throws Exception {
        var summary = mock(UsuarioRepository.ResumenDashboard.class);
        when(summary.getTotal()).thenReturn(11L); when(summary.getActivos()).thenReturn(7L);
        when(summary.getBloqueados()).thenReturn(2L); when(summary.getUsuariosActual()).thenReturn(5L);
        when(summary.getUsuariosAnterior()).thenReturn(2L);
        when(users.resumirDashboard(any(), any(), any())).thenReturn(summary);
        when(roles.count()).thenReturn(4L);
        mvc.perform(get("/api/dashboard/kpis").with(user("admin@example.test").roles("administrador")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].value").value(11))
            .andExpect(jsonPath("$[0].trend").value("up"))
            .andExpect(jsonPath("$[0].trendDescription").value("5 esta semana · 2 semana anterior"))
            .andExpect(jsonPath("$[1].value").value(7)).andExpect(jsonPath("$[2].value").value(2))
            .andExpect(jsonPath("$[3].value").value(4));
        verify(users).resumirDashboard(any(), any(), any()); verifyNoMoreInteractions(users);
    }

    @Test void administratorMeetingCountsUseOneAggregationWithZeroDefaults() throws Exception {
        when(meetings.countByEstadoAgrupado()).thenReturn(List.of(new Object[]{"programada", 2L}, new Object[]{"en_curso", 1L}));
        mvc.perform(get("/api/dashboard/reuniones-por-estado").with(user("admin@example.test").roles("administrador")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].value").value(2))
            .andExpect(jsonPath("$.items[1].value").value(1)).andExpect(jsonPath("$.items[2].value").value(0))
            .andExpect(jsonPath("$.items[3].value").value(0));
        verify(meetings).countByEstadoAgrupado(); verifyNoMoreInteractions(meetings); verifyNoInteractions(meetingAccess);
    }

    @ParameterizedTest @ValueSource(strings = {"miembro", "Jefe de Unidad", "Secretaria"})
    void nonAdministratorsCountOnlyTheRecordsReturnedByExistingAccessServices(String role) throws Exception {
        Tarea task = new Tarea(); task.setEstado("por_hacer");
        Reunion meeting = new Reunion(); meeting.setEstado("programada");
        when(taskAccess.tareasVisiblesActual()).thenReturn(List.of(task));
        when(meetingAccess.reunionesVisiblesActual()).thenReturn(List.of(meeting));
        mvc.perform(get("/api/dashboard/tareas-por-estado").with(user("usuario@example.test").roles(role)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].value").value(1)).andExpect(jsonPath("$.items[1].value").value(0));
        mvc.perform(get("/api/dashboard/reuniones-por-estado").with(user("usuario@example.test").roles(role)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].value").value(1));
        verify(taskAccess).tareasVisiblesActual(); verify(meetingAccess).reunionesVisiblesActual();
        verifyNoInteractions(tasks, meetings);
    }

    @ParameterizedTest @ValueSource(strings = {"tareas-por-estado", "reuniones-por-estado", "kpis"})
    void anonymousUserCannotReadDashboardCounts(String endpoint) throws Exception {
        mvc.perform(get("/api/dashboard/" + endpoint)).andExpect(status().isUnauthorized());
        verifyNoInteractions(tasks, meetings, taskAccess, meetingAccess);
    }

    @Test void ordinaryUserCannotReadAdministrativeIndicators() throws Exception {
        mvc.perform(get("/api/dashboard/kpis").with(user("usuario@example.test").roles("miembro"))).andExpect(status().isForbidden());
        verifyNoInteractions(users, roles);
    }
}
