package com.sigaa;

import com.sigaa.model.Permission;
import com.sigaa.model.Role;
import com.sigaa.model.AsistenciaRegistro;
import com.sigaa.model.AsistenciaSesion;
import com.sigaa.model.AsistenciaInvitado;
import com.sigaa.model.Usuario;
import com.sigaa.model.Notificacion;
import com.sigaa.repository.AsistenciaInvitadoRepository;
import com.sigaa.repository.AsistenciaRegistroRepository;
import com.sigaa.repository.AsistenciaSesionRepository;
import com.sigaa.repository.RoleRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.NotificacionRepository;
import jakarta.persistence.EntityManager;
import com.sigaa.service.InstitutionalRoles;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleEndpointAuthorizationIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AsistenciaSesionRepository asistenciaSesionRepository;

    @Autowired
    private AsistenciaRegistroRepository asistenciaRegistroRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AsistenciaInvitadoRepository asistenciaInvitadoRepository;

    @MockitoSpyBean
    private NotificacionRepository notificaciones;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void internalNotificationsKeepRecentHistoryAndOnlyTheirOwnerCanReadThem() throws Exception {
        Usuario propietario = crearUsuarioQr("Propietario de prueba", Set.of("Miembro"));
        Usuario otro = crearUsuarioQr("Otro usuario de prueba", Set.of("Miembro"));
        List<Notificacion> creadas = new ArrayList<>();
        try {
            Notificacion pendiente = notificacionDePrueba(propietario, false); creadas.add(pendiente);
            Notificacion leida = notificacionDePrueba(propietario, true); creadas.add(leida);
            Notificacion antigua = notificacionDePrueba(propietario, true); creadas.add(antigua);
            Notificacion ajena = notificacionDePrueba(otro, false); creadas.add(ajena);
            jdbc.update("UPDATE notificaciones SET created_at = ? WHERE id = ?", java.time.LocalDateTime.now().minusDays(3), antigua.getId());
            mvc.perform(get("/api/notificaciones/mias").with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[*].id")
                    .value(org.hamcrest.Matchers.containsInAnyOrder(pendiente.getId().toString(), leida.getId().toString())));
            mvc.perform(put("/api/notificaciones/{id}/leer", ajena.getId()).with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            assertEquals(Boolean.FALSE, notificaciones.findById(ajena.getId()).orElseThrow().getLeida());
            mvc.perform(get("/api/notificaciones/no-leidas").with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(1));
            mvc.perform(put("/api/notificaciones/leer-todas").with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            assertEquals(Boolean.TRUE, notificaciones.findById(pendiente.getId()).orElseThrow().getLeida());
            assertEquals(Boolean.FALSE, notificaciones.findById(ajena.getId()).orElseThrow().getLeida());
            mvc.perform(get("/api/notificaciones/mias").with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[*].id")
                    .value(org.hamcrest.Matchers.containsInAnyOrder(pendiente.getId().toString(), leida.getId().toString())));
            mvc.perform(put("/api/notificaciones/{id}/leer", pendiente.getId()).with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            mvc.perform(get("/api/notificaciones/no-leidas").with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(0));
        } finally {
            creadas.forEach(n -> notificaciones.deleteById(n.getId()));
            usuarioRepository.deleteById(propietario.getId()); usuarioRepository.deleteById(otro.getId());
        }
    }

    @Test
    void failureWhileReadingAllNotificationsRollsBackEveryReadState() throws Exception {
        Usuario propietario = crearUsuarioQr("Usuario de prueba de rollback", Set.of("Miembro"));
        List<Notificacion> creadas = new ArrayList<>();
        try {
            creadas.add(notificacionDePrueba(propietario, false)); creadas.add(notificacionDePrueba(propietario, false));
            org.mockito.Mockito.doAnswer(invocacion -> {
                entityManager.flush();
                throw new IllegalStateException("Fallo simulado después de escribir los estados");
            }).when(notificaciones).saveAll(org.mockito.ArgumentMatchers.any());
            mvc.perform(put("/api/notificaciones/leer-todas").with(user(propietario.getCorreo())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
            for (Notificacion n : creadas) assertEquals(Boolean.FALSE, notificaciones.findById(n.getId()).orElseThrow().getLeida());
        } finally {
            org.mockito.Mockito.reset(notificaciones);
            creadas.forEach(n -> notificaciones.deleteById(n.getId())); usuarioRepository.deleteById(propietario.getId());
        }
    }

    private Notificacion notificacionDePrueba(Usuario usuario, boolean leida) {
        Notificacion n = new Notificacion(); n.setUsuario(usuario); n.setTitulo("Notificación de prueba");
        n.setMensaje("Mensaje sintético"); n.setLeida(leida); return notificaciones.saveAndFlush(n);
    }

    @Test
    void qrSessionInvalidDatesReturnBadRequestWithoutSaving() throws Exception {
        long sesionesAntes = asistenciaSesionRepository.count();
        for (String fecha : List.of("\"fecha-invalida\"", "\"05/10/2026\"", "\"2026-02-30\"", "\"2025-02-29\"", "\"\"", "null", "20261005")) {
            mvc.perform(post("/api/asistencia/sesiones")
                    .with(user("qr-fecha-test@example.test").authorities(new SimpleGrantedAuthority("Configuracion QR:Crear")))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Prueba de fecha inválida\",\"fecha\":" + fecha + "}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Solicitud inválida"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("La fecha debe ser una fecha válida con formato AAAA-MM-DD."));
            assertEquals(sesionesAntes, asistenciaSesionRepository.count(), "Una fecha inválida no debe guardar ninguna sesión");
        }
    }

    @Test
    void qrSessionValidDatesAndDefaultDateKeepCreationBehavior() throws Exception {
        Usuario usuario = crearUsuarioQr("Usuario de prueba de fecha", Set.of("Miembro"));
        try {
            for (String fecha : List.of("2026-10-05", "2024-02-29", "omitida")) {
                Long id = null;
                try {
                    String body = "{\"nombre\":\"Sesión de prueba de fecha\",\"tipo\":\"reunion\""
                            + ("omitida".equals(fecha) ? "" : ",\"fecha\":\"" + fecha + "\"") + "}";
                    String response = mvc.perform(post("/api/asistencia/sesiones")
                            .with(user(usuario.getCorreo()).authorities(new SimpleGrantedAuthority("Configuracion QR:Crear")))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
                    AsistenciaSesion guardada = asistenciaSesionRepository.findById(id).orElseThrow();
                    assertEquals("omitida".equals(fecha) ? LocalDate.now() : LocalDate.parse(fecha), guardada.getFecha());
                    assertEquals(usuario.getId(), guardada.getCreadoPor());
                    assertEquals("reunion", guardada.getTipo());
                    assertEquals(Boolean.TRUE, guardada.getActivo());
                    assertEquals(Boolean.FALSE, guardada.getArchivado());
                    assertTrue(guardada.getCodigo() != null && !guardada.getCodigo().isBlank());
                } finally { if (id != null) asistenciaSesionRepository.deleteById(id); }
            }
        } finally { usuarioRepository.deleteById(usuario.getId()); }
    }

    @Test
    void qrAttendeesIncludeTheirUserOutsideTheRepositoryTransaction() throws Exception {
        Usuario usuario = crearUsuarioQr("Participante de prueba", Set.of("Miembro", "Director"));
        Usuario administrador = crearUsuarioQr("Superadmin de prueba", Set.of("Superadmin"));
        AsistenciaSesion sesion = new AsistenciaSesion();
        sesion.setCodigo("TEST-QR-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        sesion.setNombre("Validación de carga QR");
        sesion.setFecha(LocalDate.now());
        sesion.setCreadoPor(usuario.getId());
        sesion = asistenciaSesionRepository.saveAndFlush(sesion);

        List<AsistenciaRegistro> registros = new ArrayList<>();
        List<AsistenciaInvitado> invitados = new ArrayList<>();
        try {
            registros.add(registrarAsistenciaQr(sesion, usuario));
            registros.add(registrarAsistenciaQr(sesion, administrador));
            invitados.add(registrarInvitadoQr(sesion, usuario));
            invitados.add(registrarInvitadoQr(sesion, administrador));

            var asistentes = asistenciaRegistroRepository.findBySesionIdOrderByHoraEntradaAsc(sesion.getId());
            assertEquals(2, asistentes.size(), "Los roles múltiples no deben duplicar asistentes");
            assertTrue(asistentes.stream().allMatch(r -> Hibernate.isInitialized(r.getUsuario().getRoles())),
                    "Los roles necesarios para la política deben estar cargados al salir del repositorio");
            mvc.perform(get("/api/asistencia/sesiones/{codigo}/asistentes", sesion.getCodigo())
                            .with(user(usuario.getCorreo()).authorities(
                                    new SimpleGrantedAuthority("Configuracion QR:Ver"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.registrados.length()").value(1))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.registrados[0].usuarioId").value(usuario.getId()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.registrados[0].nombre").value(usuario.getNombre()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.invitados.length()").value(1))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.invitados[0].correo").value(usuario.getCorreo()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(2));
            mvc.perform(get("/api/asistencia/sesiones/{codigo}/invitados", sesion.getCodigo())
                            .with(user(usuario.getCorreo()).authorities(new SimpleGrantedAuthority("Configuracion QR:Ver"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(1));
        } finally {
            asistenciaInvitadoRepository.deleteAll(invitados);
            asistenciaRegistroRepository.deleteAll(registros);
            asistenciaSesionRepository.deleteById(sesion.getId());
            usuarioRepository.deleteById(usuario.getId());
            usuarioRepository.deleteById(administrador.getId());
        }
    }

    private Usuario crearUsuarioQr(String nombre, Set<String> nombresRoles) {
        Usuario usuario = new Usuario();
        String identidad = "qr-test-" + UUID.randomUUID();
        usuario.setNombre(nombre);
        usuario.setUsername(identidad);
        usuario.setCorreo(identidad + "@example.test");
        usuario.setPassword("password-de-prueba");
        usuario.setRoles(nombresRoles.stream().map(nombreRol -> roleRepository.findByNombre(nombreRol).orElseThrow())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new)));
        return usuarioRepository.saveAndFlush(usuario);
    }

    private AsistenciaRegistro registrarAsistenciaQr(AsistenciaSesion sesion, Usuario usuario) {
        AsistenciaRegistro registro = new AsistenciaRegistro();
        registro.setUsuario(usuario);
        registro.setSesion(sesion);
        registro.setFecha(LocalDate.now());
        registro.setHoraEntrada(LocalTime.now());
        registro.setMetodo("QR");
        registro.setEstado("presente");
        return asistenciaRegistroRepository.saveAndFlush(registro);
    }

    private AsistenciaInvitado registrarInvitadoQr(AsistenciaSesion sesion, Usuario usuario) {
        AsistenciaInvitado invitado = new AsistenciaInvitado();
        invitado.setSesionId(sesion.getId());
        invitado.setNombre(usuario.getNombre());
        invitado.setCorreo(usuario.getCorreo());
        return asistenciaInvitadoRepository.saveAndFlush(invitado);
    }

    @Test
    void sessionWithAttendanceIsClosedInsteadOfDeleted() throws Exception {
        var usuario = crearUsuarioQr("Usuario ficticio de cierre", Set.of("Miembro"));
        AsistenciaSesion sesion = new AsistenciaSesion();
        sesion.setCodigo("TEST-CLOSE-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        sesion.setNombre("Sesión con historial");
        sesion.setFecha(LocalDate.now());
        sesion.setCreadoPor(usuario.getId());
        sesion = asistenciaSesionRepository.saveAndFlush(sesion);

        AsistenciaRegistro registro = new AsistenciaRegistro();
        registro.setUsuario(usuario);
        registro.setSesion(sesion);
        registro.setFecha(LocalDate.now());
        registro.setHoraEntrada(LocalTime.now());
        registro.setMetodo("QR");
        registro.setEstado("presente");
        registro = asistenciaRegistroRepository.saveAndFlush(registro);

        try {
            mvc.perform(delete("/api/asistencia/sesiones/{id}", sesion.getId())
                            .with(user(usuario.getCorreo()).authorities(
                                    new SimpleGrantedAuthority("Configuracion QR:Eliminar"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.asistencias").value(1));

            mvc.perform(patch("/api/asistencia/sesiones/{id}/cerrar", sesion.getId())
                            .with(user(usuario.getCorreo()).authorities(
                                    new SimpleGrantedAuthority("Configuracion QR:Editar"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.activo").value(false));
        } finally {
            asistenciaRegistroRepository.deleteById(registro.getId());
            asistenciaSesionRepository.deleteById(sesion.getId());
            usuarioRepository.deleteById(usuario.getId());
        }
    }

    @TestFactory
    Stream<DynamicTest> everyActiveRoleIsCheckedAgainstEveryCriticalEndpoint() {
        List<Role> activos = roleRepository.findAll().stream()
                .filter(role -> Boolean.TRUE.equals(role.getActivo())).toList();
        // V119 conserva Administrador solo como registro histórico inactivo.
        Set<String> esperados = Set.of("Superadmin", "Jefe de Unidad", "Miembro", "Invitado",
                "Director", "Subdirector", "Secretaria");
        List<String> institucionales = activos.stream().filter(role -> Boolean.TRUE.equals(role.getSistema()))
                .map(role -> InstitutionalRoles.canonical(role.getNombre())).toList();
        assertEquals(esperados, new HashSet<>(institucionales), "Los roles institucionales deben coincidir con las migraciones");
        assertEquals(new HashSet<>(institucionales).size(), institucionales.size(), "No debe haber roles institucionales duplicados");
        List<RoleAccess> roles = activos.stream()
                .map(this::roleAccess)
                .toList();
        List<EndpointPolicy> endpoints = criticalEndpoints();

        return roles.stream().flatMap(role -> endpoints.stream().map(endpoint -> DynamicTest.dynamicTest(
                role.name() + " -> " + endpoint.name(),
                () -> assertAccess(role, endpoint))));
    }

    private void assertAccess(RoleAccess role, EndpointPolicy endpoint) throws Exception {
        Usuario fixture = crearUsuarioQr("Usuario ficticio de permisos", Set.of(role.name()));
        try {
            MockHttpServletRequestBuilder request = endpoint.request().get()
                    .with(user(fixture.getCorreo()).authorities(role.authorities().toArray(GrantedAuthority[]::new)));
            int status = mvc.perform(request).andReturn().getResponse().getStatus();
            assertTrue(status < 500, "Una validación de permisos o datos inválidos no debe producir 500");
            if (endpoint.allowed().test(role)) {
                assertNotEquals(401, status, "Un rol autenticado no debe recibir 401");
                assertNotEquals(403, status, "El rol debería tener acceso según los permisos persistidos");
            } else {
                assertEquals(403, status, role.name() + " -> " + endpoint.name()
                        + ": el endpoint aceptó un rol sin el permiso requerido");
            }
        } finally { usuarioRepository.deleteById(fixture.getId()); }
    }

    private List<EndpointPolicy> criticalEndpoints() {
        String reunionBody = "{\"titulo\":\"Prueba de permisos\",\"fecha\":\"24/08/2026\","
                + "\"horaInicio\":\"08:00\",\"horaFin\":\"09:00\"}";
        return List.of(
                endpoint("perfil propio", () -> get("/api/auth/me"), role -> true),
                authority("listar usuarios", () -> get("/api/usuarios"), "Usuarios:Ver"),
                authority("crear usuario", () -> post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Permiso\",\"apellido\":\"Temporal\"}"), "Usuarios:Crear"),
                authority("editar usuario", () -> put("/api/usuarios/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"), "Usuarios:Editar"),
                authority("eliminar usuario", () -> delete("/api/usuarios/999999"), "Usuarios:Eliminar"),
                authority("listar documentos", () -> get("/api/documentos"), "Documentos:Ver"),
                authority("listar carpetas", () -> get("/api/documentos/carpetas"), "Documentos:Ver"),
                authority("solicitar aprobación", () -> patch("/api/documentos/999999/solicitar-aprobacion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprobadorId\":1}"), "Documentos:Ver"),
                authority("resolver aprobación", () -> patch("/api/documentos/999999/resolver-aprobacion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"aprobar\"}"), "Documentos:Ver"),
                authority("listar accesos de documento", () -> get("/api/documentos/999999/compartidos"), "Documentos:Ver"),
                authority("compartir documento", () -> post("/api/documentos/999999/compartidos")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":999999,\"permiso\":\"LECTURA\"}"), "Documentos:Ver"),
                authority("retirar acceso de documento", () -> delete("/api/documentos/999999/compartidos/999999"), "Documentos:Ver"),
                authorityAny("eliminar documento", () -> delete("/api/documentos/999999"),
                        Set.of("Documentos:Crear", "Documentos:Eliminar")),
                authority("listar actas", () -> get("/api/actas"), "Actas:Ver"),
                authority("consultar acta", () -> get("/api/actas/999999"), "Actas:Ver"),
                authority("editar acta", () -> put("/api/actas/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"), "Actas:Editar"),
                authority("eliminar acta", () -> delete("/api/actas/999999"), "Actas:Eliminar"),
                authority("listar reuniones", () -> get("/api/reuniones"), "Reuniones:Ver"),
                authority("buscar reuniones", () -> get("/api/reuniones/buscar").param("q", "permiso"), "Reuniones:Ver"),
                authorityAny("editar reunión", () -> put("/api/reuniones/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(reunionBody),
                        Set.of("Reuniones:Editar", "Reuniones:Gestionar participantes")),
                authorityAny("cambiar estado de reunión", () -> patch("/api/reuniones/999999/estado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CURSO\"}"),
                        Set.of("Reuniones:Iniciar", "Reuniones:Cancelar")),
                endpoint("historial de asistencia", () -> get("/api/asistencia/historial"), role -> true),
                authority("directorio de usuarios de asistencia", () -> get("/api/asistencia/usuarios"), "Usuarios:Ver"),
                authorityAny("listar sesiones QR", () -> get("/api/asistencia/sesiones"),
                        Set.of("Configuracion QR:Ver", "Configuracion QR:Administrar")),
                authorityAny("crear sesión QR", () -> post("/api/asistencia/sesiones")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"fecha-invalida\"}"),
                        Set.of("Configuracion QR:Crear", "Configuracion QR:Administrar")),
                authorityAny("eliminar sesión QR", () -> delete("/api/asistencia/sesiones/999999"),
                        Set.of("Configuracion QR:Eliminar", "Configuracion QR:Administrar")),
                authorityAny("cerrar sesión QR", () -> patch("/api/asistencia/sesiones/999999/cerrar"),
                        Set.of("Configuracion QR:Editar", "Configuracion QR:Administrar")),
                authorityAny("consultar asistentes QR", () -> get("/api/asistencia/sesiones/INEXISTENTE/asistentes"),
                        Set.of("Configuracion QR:Ver", "Configuracion QR:Administrar", "Actas:Ver")),
                authority("listar tareas con alcance", () -> get("/api/tareas"), "Tareas:Ver"),
                role("auditoría", () -> get("/api/audit-logs/page"), "Superadmin"),
                role("respaldos", () -> get("/api/backups"), "Superadmin"),
                role("políticas de seguridad", () -> get("/api/security/policies"), "Superadmin")
        );
    }

    private EndpointPolicy authority(String name, Supplier<MockHttpServletRequestBuilder> request, String authority) {
        return endpoint(name, request, role -> role.authorityNames().contains(authority));
    }

    private EndpointPolicy authorityAny(String name, Supplier<MockHttpServletRequestBuilder> request,
                                        Set<String> authorities) {
        return endpoint(name, request, role -> role.authorityNames().stream().anyMatch(authorities::contains));
    }

    private EndpointPolicy role(String name, Supplier<MockHttpServletRequestBuilder> request, String roleName) {
        return endpoint(name, request, role -> role.name().equalsIgnoreCase(roleName));
    }

    private EndpointPolicy endpoint(String name, Supplier<MockHttpServletRequestBuilder> request,
                                    Predicate<RoleAccess> allowed) {
        return new EndpointPolicy(name, request, allowed);
    }

    private RoleAccess roleAccess(Role role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getNombre()));
        if ("superadmin".equalsIgnoreCase(role.getNombre())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_superadmin"));
            authorities.add(new SimpleGrantedAuthority("ROLE_administrador"));
        }
        role.getPermisos().stream()
                .filter(permission -> Boolean.TRUE.equals(permission.getActivo()))
                .map(this::authorityName)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        Set<String> names = authorities.stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
        return new RoleAccess(role.getNombre(), authorities, names);
    }

    private String authorityName(Permission permission) {
        return permission.getModulo() + ":" + permission.getAccion();
    }

    private record RoleAccess(String name, List<GrantedAuthority> authorities, Set<String> authorityNames) { }
    private record EndpointPolicy(String name, Supplier<MockHttpServletRequestBuilder> request,
                                  Predicate<RoleAccess> allowed) { }
}
