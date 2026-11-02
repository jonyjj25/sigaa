package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.ReunionService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReunionWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RoleRepository roles;
    @Autowired ReunionRepository reuniones;
    @Autowired ReunionService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> meetingIds = new ArrayList<>();
    private final List<Long> unitIds = new ArrayList<>();
    private Usuario creador, invitado, secretaria;
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();

    @BeforeEach void preparar() {
        creador = usuario("Creador", "Miembro");
        invitado = usuario("Invitado", "Miembro");
        secretaria = usuario("Secretaria", "Secretaria");
    }

    @AfterEach void limpiar() {
        SecurityContextHolder.clearContext();
        for (Long id : meetingIds) {
            jdbc.update("delete from audit_logs where entidad = 'Reunion' and entidad_id = ?", id);
            jdbc.update("delete from reunion_participantes_usuario where reunion_id = ?", id);
            jdbc.update("delete from reuniones where id = ?", id);
        }
        for (Long id : userIds) {
            jdbc.update("delete from notificaciones where usuario_id = ?", id);
            jdbc.update("delete from usuario_unidad where usuario_id = ?", id);
            jdbc.update("delete from usuarios where id = ?", id);
        }
        for (Long id : unitIds) jdbc.update("delete from unidades where id = ?", id);
    }

    @Test void creadorSeIncluyeUnaSolaVezInclusoSiYaFueSeleccionado() throws Exception {
        var result = crear(creador, "Miembro", List.of(creador.getId(), invitado.getId(), creador.getId()));
        assertThat(result.participantes()).containsExactlyInAnyOrder(creador.getId(), invitado.getId());
        assertThat(result.cantidad()).isEqualTo(2);
        assertThat(result.nombres()).containsExactlyInAnyOrder("Creador Reuniones", "Invitado Reuniones");
    }

    @Test void creadorSeAgregaAunqueElClienteLoOmita() throws Exception {
        var result = crear(creador, "Miembro", List.of(invitado.getId()));
        assertThat(result.participantes()).containsExactlyInAnyOrder(creador.getId(), invitado.getId());
    }

    @Test void secretariaCreaSinParticiparYNuncaRecibeAvisosSoloPorSerOrganizadora() throws Exception {
        var result = crear(secretaria, "Secretaria", List.of(invitado.getId()));
        assertThat(result.participantes()).containsExactly(invitado.getId());
        assertThat(avisos(secretaria)).isZero();
        assertThat(avisos(invitado)).isEqualTo(1);
    }

    @Test void secretariaConsultaUnaReunionAjenaSinPermisoDeGestion() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        mvc.perform(get("/api/reuniones/{id}", reunion.getId())
                .with(user(secretaria.getCorreo()).authorities(authorities("Secretaría", "Ver"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeIniciar").value(false));
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(secretaria.getCorreo()).authorities(authorities("Secretaria", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isForbidden());
        assertThat(reuniones.findById(reunion.getId()).orElseThrow().getEstado()).isEqualTo("programada");
    }

    @Test void creadorConPermisoPuedeIniciarSinSerGestorGlobal() throws Exception {
        Reunion reunion = reunion(creador, Set.of(creador, invitado));
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(creador.getCorreo()).authorities(authorities("Miembro", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("en_curso"));
        assertThat(avisos(invitado)).isEqualTo(1);
    }

    @Test void iniciarDosVecesNoDuplicaAvisosYLaEntregaRealSeConfirma() throws Exception {
        Usuario director = usuario("Director", "Director");
        Reunion reunion = reunion(director, Set.of(invitado));
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                    .with(user(director.getCorreo()).authorities(authorities("Director", "Iniciar")))
                    .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(avisos(invitado)).isEqualTo(1);
        assertThat(avisos(director)).isZero();
    }

    @Test void cancelarNoConcedePermisoParaIniciar() throws Exception {
        Usuario director = usuario("Director", "Director");
        Reunion reunion = reunion(director, Set.of(invitado));
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(director.getCorreo()).authorities(authorities("Director", "Cancelar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isForbidden());
        assertThat(avisos(invitado)).isZero();
    }

    @Test void rollbackNoCambiaElEstadoNiGeneraAvisos() {
        Usuario director = usuario("Director", "Director");
        Reunion reunion = reunion(director, Set.of(invitado));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                director.getCorreo(), "TEST_ONLY", List.of(authorities("Director", "Iniciar"))));
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            service.cambiarEstado(reunion.getId(), "en_curso");
            assertThat(avisos(invitado)).isZero();
            transaction.setRollbackOnly();
        });
        assertThat(reuniones.findById(reunion.getId()).orElseThrow().getEstado()).isEqualTo("programada");
        assertThat(avisos(invitado)).isZero();
    }

    @Test void secretariaParticipaYRecibeElInicioSoloSiFueInvitada() throws Exception {
        var creada = crear(secretaria, "Secretaria", List.of(secretaria.getId(), invitado.getId(), secretaria.getId()));
        assertThat(creada.participantes()).containsExactlyInAnyOrder(secretaria.getId(), invitado.getId());
        mvc.perform(patch("/api/reuniones/{id}/estado", creada.id())
                .with(user(secretaria.getCorreo()).authorities(authorities("Secretaria", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isOk());
        assertThat(avisos(secretaria)).isEqualTo(2); // Creación e inicio, sin duplicados.
        assertThat(avisos(invitado)).isEqualTo(2);
    }

    @Test void superadminConservaLaExclusionDeParticipacionAutomatica() throws Exception {
        Usuario superadmin = usuario("Superadmin", "Superadmin");
        var creada = crear(superadmin, "superadmin", List.of());
        assertThat(creada.participantes()).isEmpty();
        assertThat(creada.cantidad()).isZero();
        assertThat(avisos(superadmin)).isZero();
    }

    @Test void secretariaPuedeListarBuscarYConsultarPorCodigoSinSerInvitada() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        for (String path : List.of("/api/reuniones", "/api/reuniones/estado/programada", "/api/reuniones/buscar?q=Reunión")) {
            mvc.perform(get(path).with(user(secretaria.getCorreo()).authorities(authorities("Secretario", "Ver"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + reunion.getId() + ")]").isNotEmpty());
        }
        mvc.perform(get("/api/reuniones/codigo/{codigo}", reunion.getCodigo())
                .with(user(secretaria.getCorreo()).authorities(authorities("Secretaria", "Ver"))))
                .andExpect(status().isOk());
        assertThat(avisos(secretaria)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reunion_participantes_usuario where reunion_id = ? and usuario_id = ?",
                Long.class, reunion.getId(), secretaria.getId())).isZero();
    }

    @Test void consultaGlobalNoAutorizaEditarEliminarNiIniciarFueraDelAmbitoPersonal() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        for (String rol : List.of("Secretaria", "Subdirector")) {
            var permisos = new ArrayList<>(List.of(authorities(rol, "Ver", "Editar", "Eliminar", "Iniciar")));
            if (rol.equals("Subdirector")) permisos.add(new SimpleGrantedAuthority("ROLE_Secretaria"));
            var auth = user(secretaria.getCorreo()).authorities(permisos);
            mvc.perform(get("/api/reuniones/{id}", reunion.getId()).with(auth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.puedeEditar").value(false)).andExpect(jsonPath("$.puedeEliminar").value(false))
                    .andExpect(jsonPath("$.puedeIniciar").value(false));
            mvc.perform(put("/api/reuniones/{id}", reunion.getId()).with(auth).contentType("application/json")
                    .content(bodyActualizacion("programada", List.of(invitado.getId())))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/reuniones/{id}", reunion.getId()).with(auth)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId()).with(auth).contentType("application/json")
                    .content("{\"estado\":\"en_curso\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/reuniones/{id}", reunion.getId()).with(user(secretaria.getCorreo())
                .authorities(authorities("Secretaria")))).andExpect(status().isForbidden());
    }

    @Test void serCreadorOParticipanteNoSustituyeElPermisoNiElAlcanceParaIniciar() throws Exception {
        Reunion reunion = reunion(creador, Set.of(creador, invitado));
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(creador.getCorreo()).authorities(authorities("Miembro", "Crear")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(invitado.getCorreo()).authorities(authorities("Miembro", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId()).contentType("application/json")
                .content("{\"estado\":\"en_curso\"}")).andExpect(status().isUnauthorized());
        assertThat(avisos(invitado)).isZero();
    }

    @Test void permisoDeInicioNoAutorizaCancelar() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(creador.getCorreo()).authorities(authorities("Miembro", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"cancelada\"}"))
                .andExpect(status().isForbidden());
        assertThat(reuniones.findById(reunion.getId()).orElseThrow().getEstado()).isEqualTo("programada");
    }

    @Test void estadosTerminalesNoSePuedenIniciarYLaReunionInexistenteDevuelve404() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        for (String estado : List.of("cancelada", "realizada")) {
            reunion.setEstado(estado); reunion = reuniones.saveAndFlush(reunion);
            mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                    .with(user(creador.getCorreo()).authorities(authorities("Miembro", "Iniciar")))
                    .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.trace").doesNotExist());
        }
        mvc.perform(patch("/api/reuniones/{id}/estado", Long.MAX_VALUE)
                .with(user(creador.getCorreo()).authorities(authorities("Miembro", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isNotFound());
        assertThat(avisos(invitado)).isZero();
    }

    @Test void editarElFormularioNoPermiteSaltarElPermisoDeInicio() throws Exception {
        Usuario director = usuario("Director", "Director");
        Reunion reunion = reunion(director, Set.of(invitado));
        mvc.perform(put("/api/reuniones/{id}", reunion.getId())
                .with(user(director.getCorreo()).authorities(authorities("Director", "Editar")))
                .contentType("application/json").content(bodyActualizacion("en_curso", List.of(invitado.getId()))))
                .andExpect(status().isForbidden());
        assertThat(reuniones.findById(reunion.getId()).orElseThrow().getEstado()).isEqualTo("programada");
        assertThat(avisos(invitado)).isZero();
    }

    @Test void editarParticipantesNoReincorporaAutomaticamenteAlCreador() throws Exception {
        Usuario director = usuario("Director", "Director");
        Reunion reunion = reunion(director, Set.of(director, invitado));
        mvc.perform(put("/api/reuniones/{id}", reunion.getId())
                .with(user(director.getCorreo()).authorities(authorities("Director", "Editar")))
                .contentType("application/json").content(bodyActualizacion("programada", List.of(invitado.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participanteUsuarioIds.length()").value(1))
                .andExpect(jsonPath("$.participanteUsuarioIds[0]").value(invitado.getId()));
        assertThat(avisos(director)).isZero();
    }

    @Test void iniciarUnaReunionAnteriorNoModificaSusParticipantes() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(creador.getCorreo()).authorities(authorities("Miembro", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participanteUsuarioIds.length()").value(1));
        assertThat(avisos(invitado)).isEqualTo(1);
        assertThat(avisos(creador)).isZero();
    }

    @Test void unFalloAlConfirmarNoGeneraAvisos() {
        Reunion reunion = reunion(creador, Set.of(invitado));
        autenticar(creador, "Miembro", "Iniciar");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            service.cambiarEstado(reunion.getId(), "en_curso");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("TEST_ONLY_COMMIT_FAILURE"); }
            });
        })).isInstanceOf(IllegalStateException.class);
        assertThat(reuniones.findById(reunion.getId()).orElseThrow().getEstado()).isEqualTo("programada");
        assertThat(avisos(invitado)).isZero();
    }

    @Test void iniciosConcurrentesNoDuplicanAvisos() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch preparados = new CountDownLatch(2), iniciar = new CountDownLatch(1);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) resultados.add(pool.submit(() -> {
                autenticar(creador, "Miembro", "Iniciar"); preparados.countDown();
                try {
                    assertThat(iniciar.await(15, TimeUnit.SECONDS)).isTrue();
                    service.cambiarEstado(reunion.getId(), "en_curso"); return true;
                } catch (RuntimeException failure) {
                    assertThat(failure).isInstanceOfAny(org.springframework.dao.OptimisticLockingFailureException.class,
                            jakarta.persistence.OptimisticLockException.class);
                    return false;
                } finally { SecurityContextHolder.clearContext(); }
            }));
            assertThat(preparados.await(15, TimeUnit.SECONDS)).isTrue(); iniciar.countDown();
            assertThat(List.of(resultados.get(0).get(30, TimeUnit.SECONDS), resultados.get(1).get(30, TimeUnit.SECONDS))).contains(true);
            assertThat(avisos(invitado)).isEqualTo(1);
        } finally { iniciar.countDown(); pool.shutdownNow(); }
    }

    @Test void unTituloValidoLargoNoImpideEntregarElAvisoDeInicio() throws Exception {
        Reunion reunion = reunion(creador, Set.of(invitado));
        reunion.setTitulo("x".repeat(300)); reuniones.saveAndFlush(reunion);
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(creador.getCorreo()).authorities(authorities("Miembro", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isOk());
        assertThat(avisos(invitado)).isEqualTo(1);
    }

    @Test void duplicarCreaUnaReunionConElNuevoCreadorComoParticipante() throws Exception {
        Usuario director = usuario("Director", "Director");
        Reunion original = reunion(creador, Set.of(invitado));
        var response = mvc.perform(post("/api/reuniones/{id}/duplicar", original.getId())
                .with(user(director.getCorreo()).authorities(authorities("Director", "Duplicar"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var tree = json.readTree(response);
        meetingIds.add(tree.path("id").asLong());
        assertThat(tree.path("organizadorUsuarioId").asLong()).isEqualTo(director.getId());
        assertThat(json.treeToValue(tree.path("participanteUsuarioIds"), Long[].class)).contains(director.getId());
        assertThat(jdbc.queryForObject("select count(*) from reunion_participantes_usuario where reunion_id = ? and usuario_id = ?",
                Long.class, original.getId(), director.getId())).isZero();
    }

    @Test void superadminConservaGestionGlobalSinConvertirseEnParticipanteODestinatario() throws Exception {
        Usuario superadmin = usuario("Superadmin", "Superadmin");
        Reunion reunion = reunion(creador, Set.of(invitado));
        mvc.perform(patch("/api/reuniones/{id}/estado", reunion.getId())
                .with(user(superadmin.getCorreo()).authorities(authorities("superadmin", "Iniciar")))
                .contentType("application/json").content("{\"estado\":\"en_curso\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participanteUsuarioIds.length()").value(1));
        assertThat(avisos(invitado)).isEqualTo(1);
        assertThat(avisos(superadmin)).isZero();
    }

    @Test void rechazaHorariosInvalidosSinGuardar() throws Exception {
        long antes = reuniones.count();
        for (String fin : List.of("08:59", "09:00", "hora-invalida")) {
            var body = Map.of("titulo", "Prueba de horario", "fecha", "2099-10-27", "horaInicio", "09:00", "horaFin", fin);
            mvc.perform(post("/api/reuniones").with(user(creador.getCorreo()).authorities(authorities("Miembro", "Crear")))
                    .contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(reuniones.count()).isEqualTo(antes);
    }

    @Test void unidadesDeCoordinacionSeDeduplicanSinConcederAcceso() throws Exception {
        long automatica = jdbc.queryForObject("insert into unidades(nombre) values (?) returning id", Long.class, "Unidad automática " + UUID.randomUUID());
        long manual = jdbc.queryForObject("insert into unidades(nombre) values (?) returning id", Long.class, "Unidad manual " + UUID.randomUUID());
        unitIds.add(automatica); unitIds.add(manual);
        jdbc.update("insert into usuario_unidad(usuario_id, unidad_id, rol_id) values (?, ?, ?)", creador.getId(), automatica, creador.getRoles().iterator().next().getId());
        jdbc.update("insert into usuario_unidad(usuario_id, unidad_id, rol_id) values (?, ?, ?)", invitado.getId(), automatica, invitado.getRoles().iterator().next().getId());
        var body = Map.of("titulo", "Coordinación ficticia", "fecha", "2099-10-27", "horaInicio", "09:00", "horaFin", "10:00",
                "participanteUsuarioIds", List.of(invitado.getId()), "unidadIds", List.of(manual, manual));
        var result = mvc.perform(post("/api/reuniones").with(user(creador.getCorreo()).authorities(authorities("Miembro", "Crear")))
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(result).path("id").asLong(); meetingIds.add(id);
        assertThat(jdbc.queryForList("select unidad_id from reunion_unidades where reunion_id = ?", Long.class, id)).containsExactlyInAnyOrder(automatica, manual);
        Usuario jefe = usuario("Jefe ajeno", "Jefe de Unidad");
        jdbc.update("insert into usuario_unidad(usuario_id, unidad_id, rol_id) values (?, ?, ?)", jefe.getId(), manual, jefe.getRoles().iterator().next().getId());
        mvc.perform(get("/api/reuniones/{id}", id).with(user(jefe.getCorreo()).authorities(authorities("Jefe de Unidad", "Ver"))))
                .andExpect(status().isForbidden());
    }

    @Test void catalogoDeInstitucionesNoDaAccesoALaAdministracionDeOrganizacion() throws Exception {
        var actor = user(creador.getCorreo()).authorities(authorities("Miembro", "Crear"));
        mvc.perform(get("/api/organizations/seleccionables").with(actor)).andExpect(status().isOk());
        mvc.perform(get("/api/organizations").with(actor)).andExpect(status().isForbidden());
    }

    private record Creada(long id, List<Long> participantes, List<String> nombres, int cantidad) { }

    private Creada crear(Usuario actor, String rol, List<Long> participantes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("titulo", "Reunión ficticia " + UUID.randomUUID()); body.put("fecha", "2099-10-27");
        body.put("horaInicio", "09:00"); body.put("horaFin", "10:00"); body.put("participanteUsuarioIds", participantes);
        var response = mvc.perform(post("/api/reuniones").with(user(actor.getCorreo()).authorities(authorities(rol, "Crear")))
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var tree = json.readTree(response);
        long id = tree.path("id").asLong(); meetingIds.add(id);
        List<Long> ids = new ArrayList<>(); tree.path("participanteUsuarioIds").forEach(value -> ids.add(value.asLong()));
        List<String> names = new ArrayList<>(); tree.path("participantesNombres").forEach(value -> names.add(value.asText()));
        return new Creada(id, ids, names, tree.path("participantes").asInt());
    }

    private Usuario usuario(String nombre, String rol) {
        Usuario usuario = new Usuario(); usuario.setNombre(nombre); usuario.setApellido("Reuniones");
        usuario.setCorreo("reuniones-" + UUID.randomUUID() + "@example.test");
        usuario.setUsername("reuniones-" + UUID.randomUUID()); usuario.setPassword("TEST_ONLY_NOT_REAL");
        usuario.setRoles(Set.of(roles.findAll().stream().filter(value -> rol.equals(value.getNombre())).findFirst().orElseThrow()));
        usuario = usuarios.saveAndFlush(usuario); userIds.add(usuario.getId()); return usuario;
    }

    private Reunion reunion(Usuario organizador, Set<Usuario> participantes) {
        Reunion reunion = new Reunion(); reunion.setCodigo("QAR-" + UUID.randomUUID().toString().substring(0, 20));
        reunion.setTitulo("Reunión ficticia"); reunion.setFecha(LocalDate.of(2099, 10, 27));
        reunion.setHoraInicio(LocalTime.of(9, 0)); reunion.setHoraFin(LocalTime.of(10, 0));
        reunion.setOrganizadorUsuario(organizador); reunion.setOrganizador(organizador.getNombre());
        reunion.setParticipantesUsuarios(new LinkedHashSet<>(participantes));
        reunion.setParticipantes(participantes.size()); reunion.setParticipantesNombres("[]");
        reunion = reuniones.saveAndFlush(reunion); meetingIds.add(reunion.getId()); return reunion;
    }

    private long avisos(Usuario usuario) {
        return jdbc.queryForObject("select count(*) from notificaciones where usuario_id = ?", Long.class, usuario.getId());
    }

    private void autenticar(Usuario actor, String rol, String... permisos) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor.getCorreo(), "TEST_ONLY", List.of(authorities(rol, permisos))));
    }

    private String bodyActualizacion(String estado, List<Long> ids) throws Exception {
        return json.writeValueAsString(Map.of("titulo", "Reunión ficticia", "fecha", "2099-10-27",
                "horaInicio", "09:00", "horaFin", "10:00", "estado", estado, "participanteUsuarioIds", ids));
    }

    private SimpleGrantedAuthority[] authorities(String rol, String... permisos) {
        List<SimpleGrantedAuthority> values = new ArrayList<>(); values.add(new SimpleGrantedAuthority("ROLE_" + rol));
        for (String permiso : permisos) values.add(new SimpleGrantedAuthority("Reuniones:" + permiso));
        return values.toArray(SimpleGrantedAuthority[]::new);
    }
}
