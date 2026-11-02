package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.NotaNumeroService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Se ejecuta únicamente con el PostgreSQL efímero de la validación aislada. */
@SpringBootTest(properties = "app.reports.scheduler-delay-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotaDepartamentoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository instituciones;
    @Autowired OrganizationDepartmentRepository departamentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired NotaNumeroRepository numeros;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private Organization institucion;
    private OrganizationDepartment departamento;
    private OrganizationDepartment otraArea;
    private Usuario creador;
    private Usuario seguimiento;
    private NotaNumero numero;

    @BeforeEach void prepararDatosFicticios() {
        creador = usuario("nota-departamento@example.test");
        seguimiento = usuario("seguimiento-" + UUID.randomUUID() + "@example.test");
        institucion = institucion("Institución original de prueba");
        departamento = departamento(institucion, "Área original", "Persona externa original", true);
        otraArea = departamento(institucion, "Área alternativa", "Persona externa alternativa", true);
        departamento(institucion, "Área inactiva", "Persona inactiva", false);
        departamento(institucion("Institución ajena de prueba"), "Área ajena", "Persona ajena", true);
        int anio = LocalDate.now(ZoneId.of("America/Panama")).getYear();
        numero = new NotaNumero(); numero.setAnio(anio); numero.setConsecutivo(998);
        numero.setNumero(NotaNumeroService.formatear(998, anio)); numero.setEstado(NotaNumeroService.RESERVADO);
        numero.setReservadoPor(creador); numero.setReservadoDesde(LocalDateTime.now());
        numero.setReservadoHasta(LocalDateTime.now().plusMinutes(10)); numero = numeros.saveAndFlush(numero);
    }

    @Test @WithMockUser(authorities = "Notas:Ver")
    void catalogoDeNotasSoloExponeAreasActivasDeLaInstitucionSolicitada() throws Exception {
        mvc.perform(get("/api/notas/instituciones/{id}/departamentos", institucion.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].nombre").value("Área alternativa"))
            .andExpect(jsonPath("$[1].director").value("Persona externa original"))
            .andExpect(jsonPath("$[1].directorCargo").value("Jefatura de prueba"));
        // Consultar para completar una nota no concede administración del catálogo.
        mvc.perform(get("/api/organizations/{id}/departamentos", institucion.getId())).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "Notas:Crear")
    void catalogoNoPermiteUnaInstitucionDesactivada() throws Exception {
        institucion.setActivo(false); instituciones.saveAndFlush(institucion);
        mvc.perform(get("/api/notas/instituciones/{id}/departamentos", institucion.getId())).andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(authorities = "Otro:Ver")
    void catalogoRechazaUsuariosSinPermisoDeNotas() throws Exception {
        mvc.perform(get("/api/notas/instituciones/{id}/departamentos", institucion.getId())).andExpect(status().isForbidden());
    }

    @Test void catalogoRequiereAutenticacion() throws Exception {
        mvc.perform(get("/api/notas/instituciones/{id}/departamentos", institucion.getId())).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(username = "nota-departamento@example.test", authorities = {"Notas:Crear", "Notas:Ver", "Notas:Editar"})
    void guardaLaCadenaYConservaHistoriaSinReasignarSeguimiento() throws Exception {
        long notaId = crearNota(true);
        institucion.setNombre("Institución renombrada de prueba"); instituciones.saveAndFlush(institucion);
        departamento.setNombre("Área renombrada"); departamento.setDirector("Persona nueva del catálogo");
        departamento.setDirectorCargo("Cargo nuevo del catálogo"); departamento.setActivo(false); departamentos.saveAndFlush(departamento);
        em.clear();
        mvc.perform(get("/api/notas/{id}", notaId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.institucion.nombre").value("Institución original de prueba"))
            .andExpect(jsonPath("$.departamento.nombre").value("Área original"))
            .andExpect(jsonPath("$.destinatarioNombre").value("Persona externa original"))
            .andExpect(jsonPath("$.destinatarioCargo").value("Jefatura de prueba"))
            .andExpect(jsonPath("$.seguimientoUsuario.id").value(String.valueOf(seguimiento.getId())));
        actualizar(notaId, departamento.getId()).andExpect(status().isOk())
            .andExpect(jsonPath("$.departamento.nombre").value("Área original"))
            .andExpect(jsonPath("$.destinatarioNombre").value("Persona externa original"));
        actualizar(notaId, otraArea.getId()).andExpect(status().isOk())
            .andExpect(jsonPath("$.departamento.nombre").value("Área alternativa"))
            .andExpect(jsonPath("$.destinatarioNombre").value("Persona externa alternativa"))
            .andExpect(jsonPath("$.seguimientoUsuario.id").value(String.valueOf(seguimiento.getId())));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ?", Integer.class,
            seguimiento.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM nota_historial WHERE nota_id = ? AND accion = 'CAMBIAR_RESPONSABLE'",
            Integer.class, notaId)).isZero();
    }

    @Test @WithMockUser(username = "nota-departamento@example.test", authorities = {"Notas:Crear", "Notas:Ver"})
    void notasSinAreaSiguenSiendoValidasYNoSeLesInfiereUna() throws Exception {
        long notaId = crearNota(false); em.clear();
        mvc.perform(get("/api/notas/{id}", notaId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.departamento").isEmpty())
            .andExpect(jsonPath("$.destinatarioNombre").value(""))
            .andExpect(jsonPath("$.seguimientoUsuario.id").value(String.valueOf(seguimiento.getId())));
    }

    private long crearNota(boolean conArea) throws Exception {
        var datos = new java.util.HashMap<String, Object>(Map.of("numeroId", numero.getId(), "asunto", "Nota ficticia",
            "institucionId", institucion.getId(), "seguimientoUsuarioId", seguimiento.getId()));
        if (conArea) datos.put("departamentoId", departamento.getId());
        String respuesta = mvc.perform(multipart("/api/notas").file(datos(datos)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions actualizar(long notaId, long areaId) throws Exception {
        return mvc.perform(multipart("/api/notas/{id}", notaId).file(datos(Map.of("asunto", "Nota ficticia",
            "institucionId", institucion.getId(), "departamentoId", areaId, "seguimientoUsuarioId", seguimiento.getId())))
            .with(request -> { request.setMethod("PUT"); return request; }));
    }

    private MockMultipartFile datos(Map<String, Object> values) {
        return new MockMultipartFile("datos", "datos.json", "application/json", json.writeValueAsBytes(values));
    }
    private Usuario usuario(String correo) {
        Usuario usuario = new Usuario(); usuario.setNombre("Usuario ficticio"); usuario.setApellido("Prueba");
        usuario.setCorreo(correo); usuario.setUsername("nota-area-" + UUID.randomUUID()); usuario.setPassword("TEST_ONLY_NOT_REAL");
        return usuarios.saveAndFlush(usuario);
    }
    private Organization institucion(String nombre) {
        Organization item = new Organization(); item.setNombre(nombre); item.setActivo(true); return instituciones.saveAndFlush(item);
    }
    private OrganizationDepartment departamento(Organization parent, String nombre, String director, boolean activo) {
        OrganizationDepartment item = new OrganizationDepartment(); item.setOrganization(parent); item.setNombre(nombre);
        item.setDirector(director); item.setDirectorCargo("Jefatura de prueba"); item.setActivo(activo); return departamentos.saveAndFlush(item);
    }
}
