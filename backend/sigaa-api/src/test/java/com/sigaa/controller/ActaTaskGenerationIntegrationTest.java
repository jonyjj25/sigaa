package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.TareaAccessService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ActaTaskGenerationIntegrationTest {
    @Autowired ActaController controller;
    @Autowired ActaRepository actas;
    @Autowired ActaAcuerdoRepository acuerdos;
    @Autowired TareaRepository tareas;
    @Autowired UsuarioRepository usuarios;
    @Autowired RoleRepository roles;
    @Autowired TareaAccessService accesoTareas;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditLogRepository audit;
    private Acta acta;
    private Usuario responsable;

    @BeforeEach void preparar() {
        Usuario u = new Usuario(); u.setNombre("Responsable"); u.setApellido("Prueba");
        String unique = UUID.randomUUID().toString();
        u.setUsername("prueba-" + unique); u.setCorreo(unique + "@example.test"); u.setPassword("TEST_ONLY_NOT_REAL");
        u.setRoles(new HashSet<>(Set.of(roles.findAll().stream().filter(r -> "Miembro".equalsIgnoreCase(r.getNombre())).findFirst().orElseThrow())));
        responsable = usuarios.saveAndFlush(u);
        Acta a = new Acta(); a.setCodigo("ACT-TEST-" + unique); a.setFecha(LocalDate.of(2099, 1, 1));
        a.setHora(LocalTime.of(9, 0)); a.setLugar("Lugar ficticio"); a.setTitulo("Acta de prueba");
        a.setTipo("Ordinaria"); a.setEstado("Borrador"); a.setResponsable("Gestor ficticio");
        acta = actas.saveAndFlush(a);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                responsable.getCorreo(), "TEST_ONLY", List.of(new SimpleGrantedAuthority("ROLE_superadmin"), new SimpleGrantedAuthority("Actas:Editar"))));
    }

    @AfterEach void limpiar() {
        reset(audit); SecurityContextHolder.clearContext();
        jdbc.update("delete from tareas where acta_id = ?", acta.getId());
        jdbc.update("delete from audit_logs where entidad = 'Acta' and entidad_id = ?", acta.getId());
        jdbc.update("delete from actas where id = ?", acta.getId());
        jdbc.update("delete from notificaciones where usuario_id = ?", responsable.getId());
        usuarios.deleteById(responsable.getId());
    }

    @Test void generaUnaTareaVisibleConOrigenYUnSoloAvisoAunqueSeReenvieElActa() {
        var body = body("Acuerdo de prueba");
        var actualizado = controller.actualizar(acta.getId(), body);
        assertThat((List<?>) Objects.requireNonNull(actualizado.getBody()).get("acuerdosRel")).singleElement()
                .satisfies(item -> assertThat(((Map<?, ?>) item).get("fechaLimite")).isEqualTo("2099-12-31"));
        controller.actualizar(acta.getId(), body);
        List<Tarea> generadas = tareas.findByActaId(acta.getId());
        assertThat(generadas).hasSize(1);
        assertThat(generadas.getFirst().getAcuerdoClave()).hasSize(64);
        assertThat(generadas.getFirst().getFechaLimite()).isEqualTo(LocalDate.of(2099, 12, 31));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                responsable.getCorreo(), "TEST_ONLY", List.of(new SimpleGrantedAuthority("ROLE_miembro"), new SimpleGrantedAuthority("Tareas:Ver"))));
        assertThat(accesoTareas.tareasVisiblesActual()).extracting(Tarea::getId).contains(generadas.getFirst().getId());
        assertThat(jdbc.queryForObject("select count(*) from notificaciones where usuario_id = ? and titulo like 'Nueva tarea:%'", Long.class, responsable.getId())).isEqualTo(1);
    }

    @Test void noDuplicaAcuerdosConTitulosLargos() {
        var body = body("x".repeat(350));
        controller.actualizar(acta.getId(), body); controller.actualizar(acta.getId(), body);
        assertThat(tareas.findByActaId(acta.getId())).hasSize(1);
    }

    @Test void rollbackDelActaNoDejaTareaNiNotificacion() {
        doThrow(new IllegalStateException("TEST_ONLY_ROLLBACK")).when(audit).save(any(AuditLog.class));
        assertThatThrownBy(() -> controller.actualizar(acta.getId(), body("Rollback ficticio"))).isInstanceOf(RuntimeException.class);
        assertThat(tareas.findByActaId(acta.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notificaciones where usuario_id = ?", Long.class, responsable.getId())).isZero();
        assertThat(actas.findById(acta.getId()).orElseThrow().getEstado()).isEqualTo("Borrador");
    }

    private Map<String, Object> body(String description) {
        return Map.of("estado", "Terminada", "acuerdosRel", List.of(Map.of("descripcion", description,
                "responsable", responsable.getCorreo(), "fechaLimite", "2099-12-31")));
    }
}
