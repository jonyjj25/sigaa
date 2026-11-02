package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reutiliza la cadena y las políticas reales; solo repositorios simulados y datos ficticios. */
@SpringJUnitWebConfig(AsistenciaResourceAuthorizationTest.Config.class)
class AsistenciaResourceAuthorizationTest {
    @Configuration
    @Import({ActaResourceAuthorizationTest.Config.class, AsistenciaController.class})
    static class Config {}

    @Autowired WebApplicationContext context;
    @Autowired ActaRepository actas;
    @Autowired UsuarioRepository usuarios;
    @Autowired AsistenciaSesionRepository sesiones;
    @Autowired AsistenciaRegistroRepository registros;
    @Autowired AsistenciaInvitadoRepository invitados;
    @Autowired ActaParticipanteRepository participantes;
    @Autowired ActaAcuerdoRepository acuerdos;
    @Autowired ActaFirmaRepository firmas;
    @Autowired ActaAdjuntoRepository adjuntos;
    @Autowired AuditLogRepository auditoria;
    @Autowired TareaRepository tareas;
    private MockMvc mvc;
    private Usuario propietario;
    private Acta actaPropia;
    private AsistenciaSesion vinculada;
    private AsistenciaSesion propia;
    private AsistenciaSesion ajena;
    private List<AsistenciaSesion> todas;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void preparar() {
        reset(new Object[] {actas, usuarios, sesiones, registros, invitados, participantes,
                acuerdos, firmas, adjuntos, auditoria, tareas});
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        propietario = usuario(1L, "propietario@example.test", "Participante Prueba");
        Usuario otro = usuario(2L, "otro@example.test", "Otro Prueba");
        when(usuarios.findByCorreoIgnoreCase(propietario.getCorreo())).thenReturn(Optional.of(propietario));
        when(usuarios.findByCorreo(propietario.getCorreo())).thenReturn(Optional.of(propietario));
        when(usuarios.findByCorreoIgnoreCase(otro.getCorreo())).thenReturn(Optional.of(otro));
        vinculada = sesion(40L, null);
        propia = sesion(42L, 1L);
        ajena = sesion(43L, 2L);
        AsistenciaSesion vinculadaAjena = sesion(41L, null);
        AsistenciaSesion sinOrigen = sesion(44L, null);
        todas = List.of(vinculada, vinculadaAjena, propia, ajena, sinOrigen);
        for (AsistenciaSesion s : todas) {
            when(sesiones.findById(s.getId())).thenReturn(Optional.of(s));
            when(sesiones.findByCodigo(s.getCodigo())).thenReturn(Optional.of(s));
        }
        when(sesiones.findByArchivadoFalseOrderByFechaDescCreadoEnDesc()).thenReturn(todas);
        when(sesiones.findByArchivadoTrueOrderByFechaDescCreadoEnDesc()).thenReturn(todas);
        when(sesiones.save(any(AsistenciaSesion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        actaPropia = acta(20L, vinculada);
        Acta actaAjena = acta(21L, vinculadaAjena);
        when(actas.findById(20L)).thenReturn(Optional.of(actaPropia));
        when(actas.findById(21L)).thenReturn(Optional.of(actaAjena));
        when(actas.findBySesionId(40L)).thenReturn(List.of(actaPropia));
        when(actas.findBySesionId(41L)).thenReturn(List.of(actaAjena));
        when(actas.findByParticipanteUsuario(propietario.getCorreo(), propietario.getNombre())).thenReturn(List.of(actaPropia));
        when(actas.findByParticipanteUsuario(otro.getCorreo(), otro.getNombre())).thenReturn(List.of(actaAjena));
        when(actas.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AsistenciaRegistro registro = new AsistenciaRegistro();
        registro.setId(50L); registro.setUsuario(propietario); registro.setSesion(vinculada);
        registro.setFecha(LocalDate.now()); registro.setHoraEntrada(LocalTime.of(10, 0));
        when(registros.findBySesionIdOrderByHoraEntradaAsc(40L)).thenReturn(List.of(registro));
        when(registros.findByUsuarioIdAndSesionIsNotNullOrderByFechaDesc(1L)).thenReturn(List.of(registro));
        AsistenciaInvitado invitado = new AsistenciaInvitado();
        invitado.setId(60L); invitado.setSesionId(40L); invitado.setNombre("Invitado Prueba");
        invitado.setCorreo("invitado@example.test"); invitado.setTelefono("TEST_ONLY_PHONE");
        invitado.setInstitucion("Institucion ficticia"); invitado.setCargo("Cargo ficticio");
        when(invitados.findBySesionIdOrderByHoraRegistroDesc(40L)).thenReturn(List.of(invitado));
    }

    @ParameterizedTest @ValueSource(strings = {"asistentes", "invitados"})
    void participanteConPermisoYActaPuedeConsultar(String recurso) throws Exception {
        mvc.perform(get("/api/asistencia/sesiones/SESION-40/" + recurso).with(actor("Actas:Ver")))
            .andExpect(status().isOk());
        verify(actas).findByParticipanteUsuario(propietario.getCorreo(), propietario.getNombre());
        verify(invitados).findBySesionIdOrderByHoraRegistroDesc(40L);
    }

    @ParameterizedTest @ValueSource(strings = {"Actas:Ver", "Configuracion QR:Ver"})
    void permisoGeneralNoPermiteConocerUnaSesionAjena(String permiso) throws Exception {
        for (String codigo : List.of("SESION-41", "SESION-43", "SESION-44", "INEXISTENTE")) {
            for (String recurso : List.of("asistentes", "invitados")) {
                mvc.perform(get("/api/asistencia/sesiones/" + codigo + "/" + recurso).with(actor(permiso)))
                    .andExpect(status().isNotFound()).andExpect(content().string(""));
            }
        }
        verifyNoInteractions(registros, invitados);
    }

    @Test void tenerLaMismaFechaYNombreDeUnaReunionNoConcedeAcceso() throws Exception {
        ajena.setNombre(vinculada.getNombre());
        mvc.perform(get("/api/asistencia/sesiones/SESION-43/asistentes").with(actor("Reuniones:Ver")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/asistencia/sesiones/SESION-43/asistentes").with(actor("Actas:Ver")))
            .andExpect(status().isNotFound());
        verifyNoInteractions(registros, invitados);
    }

    @Test void propietarioDeSesionIndependientePuedeConsultarConPermiso() throws Exception {
        mvc.perform(get("/api/asistencia/sesiones/SESION-42/asistentes").with(actor("Configuracion QR:Ver")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sesion.id").value(42));
    }

    @ParameterizedTest @ValueSource(strings = {"asistentes", "invitados"})
    void accesoSinPermisoYAnonimoSeRechazanAntesDeLeerLaSesion(String recurso) throws Exception {
        mvc.perform(get("/api/asistencia/sesiones/SESION-40/" + recurso).with(actor("Actas:Editar")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/asistencia/sesiones/SESION-40/" + recurso)).andExpect(status().isUnauthorized());
        verifyNoInteractions(sesiones, registros, invitados);
    }

    @ParameterizedTest @ValueSource(strings = {"ROLE_superadmin", "ROLE_director"})
    void gestoresLegitimosConPermisoPuedenConsultar(String rol) throws Exception {
        var gestor = user("gestor@example.test").authorities(autoridades(rol, "Configuracion QR:Ver"));
        mvc.perform(get("/api/asistencia/sesiones/SESION-43/asistentes").with(gestor)).andExpect(status().isOk());
        mvc.perform(get("/api/asistencia/sesiones/SESION-43/invitados").with(gestor)).andExpect(status().isOk());
    }

    @Test void administrarQrEsElPrivilegioExplicitoDeGestion() throws Exception {
        mvc.perform(get("/api/asistencia/sesiones/SESION-43/asistentes").with(actor("Configuracion QR:Administrar")))
            .andExpect(status().isOk());
    }

    @Test void superadminConservaLaExcepcionExplicitaDelControladorQr() throws Exception {
        mvc.perform(get("/api/asistencia/sesiones/SESION-43/asistentes").with(user("gestor@example.test")
            .authorities(autoridades("ROLE_superadmin", "ROLE_administrador")))).andExpect(status().isOk());
    }

    @Test void cargoDeDirectorSinPermisoFuncionalNoConcedeLectura() throws Exception {
        mvc.perform(get("/api/asistencia/sesiones/SESION-43/asistentes").with(user("gestor@example.test")
            .authorities(autoridades("ROLE_director")))).andExpect(status().isForbidden());
        verifyNoInteractions(sesiones, registros, invitados);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void listarSesionesFiltraAntesDeConsultarContadores(boolean archivadas) throws Exception {
        todas.forEach(s -> s.setArchivado(archivadas));
        mvc.perform(get("/api/asistencia/sesiones").param("archivadas", String.valueOf(archivadas))
                .with(actor("Configuracion QR:Ver")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(40)).andExpect(jsonPath("$[1].id").value(42));
        for (long id : List.of(41L, 43L, 44L)) {
            verify(registros, never()).countBySesionId(id);
            verify(invitados, never()).countBySesionId(id);
            verify(actas, never()).countBySesionId(id);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"cerrar", "archivar", "restaurar"})
    void cambiarElIdNoPermiteObtenerNiModificarOtraSesion(String accion) throws Exception {
        mvc.perform(patch("/api/asistencia/sesiones/43/" + accion).with(actor("Configuracion QR:Editar")))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/asistencia/sesiones/99/" + accion).with(actor("Configuracion QR:Editar")))
            .andExpect(status().isNotFound());
        verify(sesiones, never()).save(any());
        verifyNoInteractions(registros, invitados);
        assertThat(ajena.getActivo()).isTrue();
        assertThat(ajena.getArchivado()).isFalse();
    }

    @Test void eliminarSesionAjenaNoExponeDependencias() throws Exception {
        mvc.perform(delete("/api/asistencia/sesiones/43").with(actor("Configuracion QR:Eliminar")))
            .andExpect(status().isNotFound()).andExpect(content().string(""));
        verifyNoInteractions(registros, invitados);
        verify(actas, never()).countBySesionId(anyLong());
        verify(sesiones, never()).delete(any());
    }

    @Test void propietarioConPermisoPuedeCerrarSuSesion() throws Exception {
        mvc.perform(patch("/api/asistencia/sesiones/42/cerrar").with(actor("Configuracion QR:Editar")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        verify(sesiones).save(propia);
    }

    @Test void idConocidoNoSustituyeAutenticacionNiPermiso() throws Exception {
        mvc.perform(patch("/api/asistencia/sesiones/42/cerrar").with(actor("Actas:Ver")))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/asistencia/sesiones/42/cerrar")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/asistencia/sesiones/42")).andExpect(status().isUnauthorized());
        verifyNoInteractions(sesiones);
    }

    @Test void detallePublicoSoloIncluyeLosTresCamposNecesariosParaQr() throws Exception {
        String body = mvc.perform(get("/api/asistencia/sesiones/SESION-40").param("actaId", "21").param("usuarioId", "2"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Set<String> campos = new HashSet<>(); json.readTree(body).fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("nombre", "fecha", "horaInicio");
        assertThat(body).doesNotContain("correo", "roles", "permisos", "usuario", "descripcion", "creadoPor", "totalInvitados");
        verifyNoInteractions(registros, invitados, actas, usuarios);
    }

    @ParameterizedTest @ValueSource(strings = {"inactiva", "archivada", "expirada"})
    void publicQrNoPermiteRegistrarEnSesionNoDisponible(String estado) throws Exception {
        if (estado.equals("inactiva")) vinculada.setActivo(false);
        if (estado.equals("archivada")) vinculada.setArchivado(true);
        if (estado.equals("expirada")) vinculada.setQrExpiraEn(LocalDateTime.now().minusMinutes(1));
        mvc.perform(get("/api/asistencia/sesiones/SESION-40")).andExpect(status().isNotFound());
        mvc.perform(invitacion()).andExpect(status().isBadRequest());
        verify(invitados, never()).save(any());
    }

    @Test void invitadoPublicoPuedeRegistrarseSinRecibirDatosDeTerceros() throws Exception {
        String body = mvc.perform(invitacion()).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("correo", "telefono", "roles", "permisos", "usuario", "Invitado Prueba");
        verify(invitados).save(any());
        verify(invitados, never()).findBySesionIdOrderByHoraRegistroDesc(anyLong());
    }

    @Test void invitacionRepetidaNoPermiteComprobarAsistenciaDeOtroCorreo() throws Exception {
        String primera = mvc.perform(invitacion()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        when(invitados.existsBySesionIdAndCorreoIgnoreCase(40L, "nuevo@example.test")).thenReturn(true);
        String repetida = mvc.perform(invitacion()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repetida)).isEqualTo(json.readTree(primera));
        verify(invitados, times(1)).save(any());
    }

    @Test void rechazoPublicoNoRevelaRolesAdministrativos() throws Exception {
        Usuario administrador = usuario(3L, "nuevo@example.test", "Administrador ficticio");
        Role rol = new Role(); rol.setNombre("Superadmin"); administrador.getRoles().add(rol);
        when(usuarios.findByCorreoIgnoreCase("nuevo@example.test")).thenReturn(Optional.of(administrador));
        String body = mvc.perform(invitacion()).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Administrador", "Superadmin", "roles", "nuevo@example.test");
        verify(invitados, never()).save(any());
    }

    @Test void usuariosInternosNoSonUnDirectorioParaElQr() throws Exception {
        mvc.perform(get("/api/asistencia/usuarios").with(actor("Actas:Ver"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/asistencia/usuarios")).andExpect(status().isUnauthorized());
        verify(usuarios, never()).findAll();
        when(usuarios.findAll()).thenReturn(List.of(propietario));
        String body = mvc.perform(get("/api/asistencia/usuarios").with(actor("Usuarios:Ver")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].correo").value(propietario.getCorreo()))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("roles", "permisos", "sesion", "participantesUsuarios");
    }

    @Test void registrarUsuarioSoloBuscaAlPropioUsuarioAunqueSeManipuleUid() throws Exception {
        mvc.perform(post("/api/asistencia/escanear").contentType(MediaType.APPLICATION_JSON)
            .content("{\"uid\":2,\"codigo\":\"SESION-40\"}").with(actor()))
            .andExpect(status().isForbidden());
        verify(usuarios, never()).findById(anyLong());
        verify(registros, never()).save(any());
        mvc.perform(post("/api/asistencia/escanear").contentType(MediaType.APPLICATION_JSON)
            .content("{\"uid\":1,\"codigo\":\"SESION-40\"}").with(actor()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(registros).save(argThat(r -> r.getUsuario() == propietario));
    }

    @Test void historialIgnoraIdsAjenosYConsultaSoloLaIdentidadAutenticada() throws Exception {
        mvc.perform(get("/api/asistencia/historial").param("usuarioId", "2").with(actor()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].usuarioId").value(1));
        verify(registros).findByUsuarioIdAndSesionIsNotNullOrderByFechaDesc(1L);
        verify(registros, never()).findByUsuarioIdAndSesionIsNotNullOrderByFechaDesc(2L);
    }

    @Test void rosterPrivadoNoSerializaEntidadesNiDatosInnecesarios() throws Exception {
        Role rol = new Role(); rol.setNombre("Rol interno ficticio"); propietario.getRoles().add(rol);
        String body = mvc.perform(get("/api/asistencia/sesiones/SESION-40/asistentes").with(actor("Actas:Ver")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("roles", "permisos", "Rol interno ficticio", "telefono", "institucion", "cargo", "descripcion", "creadoEn", "creadoPor");
        String guests = mvc.perform(get("/api/asistencia/sesiones/SESION-40/invitados").with(actor("Actas:Ver")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(guests).doesNotContain("telefono", "institucion", "cargo", "roles", "permisos");
    }

    @Test void noPuedeVincularUnaSesionAjenaParaConsultarSusAsistentesDesdeUnActaPropia() throws Exception {
        mvc.perform(put("/api/actas/20").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sesionId\":43}").with(actor("Actas:Editar")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/actas").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sesionId\":43}").with(actor("Actas:Crear")))
            .andExpect(status().isForbidden());
        assertThat(actaPropia.getSesion()).isSameAs(vinculada);
        verify(actas, never()).save(any());
        verify(actas, never()).saveAndFlush(any());
        verifyNoInteractions(registros, invitados);
    }

    @Test void puedeVincularUnaSesionPropiaConElPermisoFuncionalDelActa() throws Exception {
        mvc.perform(put("/api/actas/20").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sesionId\":42}").with(actor("Actas:Editar")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sesion.id").value("42"));
        assertThat(actaPropia.getSesion()).isSameAs(propia);
    }

    @Test void sesionGeneradaDesdeActaRegistraElCreadorAutenticadoSinInferirDatosHistoricos() throws Exception {
        when(actas.save(any())).thenAnswer(invocation -> {
            Acta a = invocation.getArgument(0); a.setId(25L); return a;
        });
        when(sesiones.save(any())).thenAnswer(invocation -> {
            AsistenciaSesion s = invocation.getArgument(0); s.setId(45L); return s;
        });
        mvc.perform(post("/api/actas").contentType(MediaType.APPLICATION_JSON)
            .content("{\"titulo\":\"Acta ficticia\",\"fecha\":\"" + LocalDate.now() + "\",\"hora\":\"10:00\",\"crearSesion\":true}")
            .with(actor("Actas:Crear"))).andExpect(status().isOk());
        verify(sesiones).save(argThat(s -> s.getCreadoPor().equals(1L)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder invitacion() {
        return post("/api/asistencia/invitado").contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigoSesion\":\"SESION-40\",\"nombre\":\"Nuevo ficticio\",\"correo\":\"nuevo@example.test\"}");
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor actor(String... permisos) {
        List<String> valores = new ArrayList<>(List.of(permisos)); valores.add("ROLE_miembro");
        return user("propietario@example.test").authorities(autoridades(valores.toArray(String[]::new)));
    }

    private static SimpleGrantedAuthority[] autoridades(String... nombres) {
        return Arrays.stream(nombres).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
    }

    private static Usuario usuario(Long id, String correo, String nombre) {
        Usuario u = new Usuario(); u.setId(id); u.setCorreo(correo); u.setNombre(nombre); u.setActivo(true); return u;
    }

    private static AsistenciaSesion sesion(Long id, Long creadoPor) {
        AsistenciaSesion s = new AsistenciaSesion(); s.setId(id); s.setCodigo("SESION-" + id);
        s.setNombre("Sesion ficticia " + id); s.setFecha(LocalDate.now()); s.setHoraInicio(LocalTime.of(10, 0));
        s.setCreadoPor(creadoPor); s.setQrExpiraEn(LocalDateTime.now().plusHours(1)); return s;
    }

    private static Acta acta(Long id, AsistenciaSesion sesion) {
        Acta a = new Acta(); a.setId(id); a.setCodigo("ACT-PRUEBA-" + id); a.setTitulo("Acta ficticia " + id);
        a.setFecha(LocalDate.now()); a.setHora(LocalTime.of(10, 0)); a.setEstado("Borrador"); a.setSesion(sesion); return a;
    }
}
