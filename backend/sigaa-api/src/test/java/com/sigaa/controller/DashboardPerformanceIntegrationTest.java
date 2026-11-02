package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Mediciones de SQL con PostgreSQL temporal y datos exclusivamente ficticios. */
@SpringBootTest(properties = {"app.reports.scheduler-delay-ms=3600000", "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF",
    "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.orm.jdbc=OFF",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DashboardPerformanceIntegrationTest {
    @Autowired DashboardController dashboard;
    @Autowired NotificacionController notifications;
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory factory;
    @Autowired MockMvc mvc;
    @Autowired NotificacionRepository notificationRepository;

    @Test @WithMockUser(username = "rendimiento@example.test", roles = "administrador")
    void measureAdministrativeKpisWithFiveHundredFictitiousUsers() {
        for (int i=0; i<500; i++) {
            Usuario user = ficticio("rendimiento-" + i + "@example.test");
            user.setActivo(i % 3 != 0); user.setBloqueado(i % 5 == 0); users.save(user);
        }
        entityManager.flush(); entityManager.clear();
        long expectedUsers = users.count(), expectedActive = users.countByActivoTrue(), expectedBlocked = users.countByBloqueadoTrue();
        Statistics stats = factory.unwrap(SessionFactory.class).getStatistics();
        var times = new ArrayList<Long>(); long statements = 0;
        for (int i=0; i<6; i++) {
            entityManager.clear(); stats.clear(); long start = System.nanoTime();
            var cards = dashboard.kpis();
            long elapsed = System.nanoTime() - start;
            assertThat(cards.get(0).get("value")).isEqualTo(expectedUsers);
            assertThat(cards.get(1).get("value")).isEqualTo(expectedActive);
            assertThat(cards.get(2).get("value")).isEqualTo(expectedBlocked);
            statements = stats.getPrepareStatementCount();
            assertThat(statements).as("Una agregación de usuarios y un conteo de roles").isEqualTo(2);
            assertThat(stats.getEntityLoadCount()).isZero();
            if (i > 0) times.add(elapsed);
        }
        Collections.sort(times);
        System.out.printf(Locale.ROOT, "PERF_ADMIN_KPIS statements=%d median_ms=%.3f rows_loaded=0 fixture_users=500%n", statements, times.get(2)/1_000_000d);
    }

    @Test @WithMockUser(username = "notificaciones-perf@example.test")
    void measureNotificationPollingWithoutLoadingUnneededAuthorizationEntities() {
        Usuario user = ficticio("notificaciones-perf@example.test");
        for (int i=0; i<3; i++) {
            Role role = new Role("Rendimiento ficticio " + i, "Solo prueba");
            for (int p=0; p<4; p++) {
                Permission permission = new Permission("Prueba " + i, "Acción " + p, "Ficticio", "Solo prueba");
                entityManager.persist(permission); role.getPermisos().add(permission);
            }
            role = roles.saveAndFlush(role); user.getRoles().add(role);
        }
        users.saveAndFlush(user); entityManager.clear();
        Statistics stats = factory.unwrap(SessionFactory.class).getStatistics();
        var auth = new UsernamePasswordAuthenticationToken("notificaciones-perf@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_miembro")));
        var times = new ArrayList<Long>(); long statements = 0, entities = 0;
        for (int i=0; i<6; i++) {
            entityManager.clear(); stats.clear(); long start = System.nanoTime();
            assertThat(notifications.misNotificaciones(auth)).isEmpty();
            long elapsed = System.nanoTime() - start;
            statements = stats.getPrepareStatementCount(); entities = stats.getEntityLoadCount();
            assertThat(statements).as("Solo identidad escalar y notificaciones recientes").isEqualTo(2);
            assertThat(entities).as("El sondeo no necesita cargar usuario, roles ni permisos").isZero();
            if (i > 0) times.add(elapsed);
        }
        Collections.sort(times);
        System.out.printf(Locale.ROOT, "PERF_NOTIFICATIONS statements=%d median_ms=%.3f authorization_entities_loaded=%d%n", statements, times.get(2)/1_000_000d, entities);
    }

    private Usuario ficticio(String correo) {
        Usuario user = new Usuario(); user.setNombre("Persona ficticia"); user.setApellido("Prueba");
        user.setCorreo(correo); user.setUsername(correo); user.setPassword("TEST_ONLY_NOT_REAL"); return user;
    }

    @Test @WithMockUser(username = "notificaciones-alcance@example.test")
    void notificationEndpointsKeepOwnershipChecksWithScalarIdentity() throws Exception {
        Usuario owner = users.save(ficticio("notificaciones-alcance@example.test"));
        Usuario other = users.save(ficticio("notificaciones-otro@example.test"));
        Notificacion propia = notificacion(owner); Notificacion ajena = notificacion(other);
        entityManager.flush(); entityManager.clear();
        mvc.perform(get("/api/notificaciones/mias")).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(propia.getId().toString()));
        mvc.perform(get("/api/notificaciones/no-leidas")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(put("/api/notificaciones/{id}/leer", ajena.getId())).andExpect(status().isForbidden());
        mvc.perform(put("/api/notificaciones/{id}/leer", propia.getId())).andExpect(status().isOk());
        mvc.perform(put("/api/notificaciones/leer-todas")).andExpect(status().isOk());
        entityManager.flush(); entityManager.clear();
        assertThat(notificationRepository.findById(propia.getId()).orElseThrow().getLeida()).isTrue();
        assertThat(notificationRepository.findById(ajena.getId()).orElseThrow().getLeida()).isFalse();
    }

    @Test void notificationEndpointsRejectAnonymousRequests() throws Exception {
        for (String endpoint : List.of("mias", "no-leidas"))
            mvc.perform(get("/api/notificaciones/" + endpoint)).andExpect(status().isUnauthorized());
        for (String endpoint : List.of("1/leer", "leer-todas"))
            mvc.perform(put("/api/notificaciones/" + endpoint)).andExpect(status().isUnauthorized());
    }

    private Notificacion notificacion(Usuario owner) {
        Notificacion item = new Notificacion(); item.setUsuario(owner); item.setTitulo("Notificación ficticia");
        entityManager.persist(item); return item;
    }

    @Test
    void aggregationPreservesTheOriginalCountsAtPeriodBoundariesAndWithNullUpdateDates() {
        var fin = java.time.LocalDateTime.of(2026,10,19,12,0);
        var actual = fin.minusDays(7); var anterior = fin.minusDays(14);
        var fechas = List.of(fin, actual, anterior, anterior.minusNanos(1000), actual.minusNanos(1000));
        for (int i=0; i<fechas.size(); i++) {
            Usuario user = ficticio("limite-" + i + "@example.test");
            user.setActivo(i % 2 == 0); user.setBloqueado(i % 3 != 0);
            org.springframework.test.util.ReflectionTestUtils.setField(user,"creadoEn",fechas.get(i));
            org.springframework.test.util.ReflectionTestUtils.setField(user,"actualizadoEn",i == 3 ? null : fechas.get(i));
            users.save(user);
        }
        entityManager.flush(); entityManager.clear();
        var resumen = users.resumirDashboard(anterior,actual,fin);
        assertThat(resumen.getTotal()).isEqualTo(users.count());
        assertThat(resumen.getActivos()).isEqualTo(users.countByActivoTrue());
        assertThat(resumen.getBloqueados()).isEqualTo(users.countByBloqueadoTrue());
        assertThat(resumen.getUsuariosActual()).isEqualTo(users.countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(actual,fin));
        assertThat(resumen.getUsuariosAnterior()).isEqualTo(users.countByCreadoEnGreaterThanEqualAndCreadoEnLessThan(anterior,actual));
        assertThat(resumen.getActivosActual()).isEqualTo(users.countByActivoTrueAndCreadoEnGreaterThanEqualAndCreadoEnLessThan(actual,fin));
        assertThat(resumen.getActivosAnterior()).isEqualTo(users.countByActivoTrueAndCreadoEnGreaterThanEqualAndCreadoEnLessThan(anterior,actual));
        assertThat(resumen.getBloqueadosActual()).isEqualTo(users.countByBloqueadoTrueAndActualizadoEnGreaterThanEqualAndActualizadoEnLessThan(actual,fin));
        assertThat(resumen.getBloqueadosAnterior()).isEqualTo(users.countByBloqueadoTrueAndActualizadoEnGreaterThanEqualAndActualizadoEnLessThan(anterior,actual));
    }
}
