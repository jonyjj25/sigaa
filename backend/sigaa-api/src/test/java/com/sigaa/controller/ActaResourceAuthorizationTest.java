package com.sigaa.controller;

import com.sigaa.config.GlobalExceptionHandler;
import com.sigaa.config.SecurityConfig;
import com.sigaa.model.Acta;
import com.sigaa.model.Usuario;
import com.sigaa.repository.*;
import com.sigaa.security.JwtFilter;
import com.sigaa.security.JwtUtil;
import com.sigaa.service.SecurityIpService;
import com.sigaa.service.SecurityPolicyService;
import com.sigaa.service.UsuarioService;
import com.sigaa.service.ActaAccessService;
import com.sigaa.service.AsistenciaAccessService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Seguridad HTTP y autorización por recurso reales, sin base de datos ni archivos institucionales. */
@SpringJUnitWebConfig(ActaResourceAuthorizationTest.Config.class)
class ActaResourceAuthorizationTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, GlobalExceptionHandler.class, ActaController.class, UsuarioController.class,
        ActaAccessService.class, AsistenciaAccessService.class})
    static class Config {
        @Bean SecurityPolicyService policy() { return mock(SecurityPolicyService.class); }
        @Bean UserDetailsService userDetails() { return mock(UserDetailsService.class); }
        @Bean JwtFilter jwtFilter(UsuarioRepository usuarios, UserDetailsService userDetails) {
            return new JwtFilter(mock(JwtUtil.class), userDetails, mock(SecurityIpService.class), usuarios, mock(SecurityPolicyService.class));
        }
        @Bean ActaRepository actas() { return mock(ActaRepository.class); }
        @Bean ActaParticipanteRepository participantes() { return mock(ActaParticipanteRepository.class); }
        @Bean ActaAcuerdoRepository acuerdos() { return mock(ActaAcuerdoRepository.class); }
        @Bean ActaAdjuntoRepository adjuntos() { return mock(ActaAdjuntoRepository.class); }
        @Bean ActaFirmaRepository firmas() { return mock(ActaFirmaRepository.class); }
        @Bean AuditLogRepository auditoria() { return mock(AuditLogRepository.class); }
        @Bean AsistenciaSesionRepository sesiones() { return mock(AsistenciaSesionRepository.class); }
        @Bean AsistenciaRegistroRepository registros() { return mock(AsistenciaRegistroRepository.class); }
        @Bean AsistenciaInvitadoRepository invitados() { return mock(AsistenciaInvitadoRepository.class); }
        @Bean TareaRepository tareas() { return mock(TareaRepository.class); }
        @Bean com.sigaa.service.InternalNotificationService notifications() { return mock(com.sigaa.service.InternalNotificationService.class); }
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean UsuarioService usuarioService() { return mock(UsuarioService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired ActaRepository actas;
    @Autowired ActaParticipanteRepository participantes;
    @Autowired ActaAcuerdoRepository acuerdos;
    @Autowired ActaAdjuntoRepository adjuntos;
    @Autowired ActaFirmaRepository firmas;
    @Autowired AuditLogRepository auditoria;
    @Autowired AsistenciaSesionRepository sesiones;
    @Autowired AsistenciaRegistroRepository registros;
    @Autowired AsistenciaInvitadoRepository invitados;
    @Autowired TareaRepository tareas;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioService usuarioService;
    private MockMvc mvc;
    private Acta acta;

    @BeforeEach void preparar() {
        reset(actas, participantes, acuerdos, adjuntos, firmas, auditoria, sesiones, registros, invitados, tareas, usuarios, usuarioService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        acta = new Acta();
        acta.setId(20L); acta.setCodigo("ACT-PRUEBA-020"); acta.setTitulo("Acta original");
        acta.setFecha(LocalDate.of(2026, 10, 8)); acta.setHora(LocalTime.of(10, 0));
        acta.setEstado("Borrador");
        when(actas.findById(20L)).thenReturn(Optional.of(acta));
        when(actas.saveAndFlush(any(Acta.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Usuario participante = usuario(1L, "participante@example.test", "Participante");
        Usuario ajeno = usuario(2L, "ajeno@example.test", "Ajeno");
        when(usuarios.findByCorreoIgnoreCase(participante.getCorreo())).thenReturn(Optional.of(participante));
        when(usuarios.findByCorreoIgnoreCase(ajeno.getCorreo())).thenReturn(Optional.of(ajeno));
        when(actas.findByParticipanteUsuario(participante.getCorreo(), "Participante Prueba")).thenReturn(List.of(acta));
        when(actas.findByParticipanteUsuario(ajeno.getCorreo(), "Ajeno Prueba")).thenReturn(List.of());
    }

    @Test void participanteConEditarActualizaSinNecesitarCrear() throws Exception {
        mvc.perform(actualizacion("{\"titulo\":\"Acta actualizada\"}")
                .with(user("participante@example.test").authorities(autoridades("ROLE_miembro", "Actas:Editar"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Acta actualizada"));
        verify(actas).saveAndFlush(acta);
        verify(auditoria).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"titulo\":\"Contenido ajeno\"}",
        "{\"estado\":\"Terminada\"}",
        "{\"participantes\":[{\"nombre\":\"Ajeno\",\"apellido\":\"Prueba\",\"correo\":\"ajeno@example.test\"}]}",
        "{\"acuerdosRel\":[{\"descripcion\":\"Acuerdo ajeno\"}]}",
        "{\"firmas\":[{\"nombre\":\"Firma ajena\"}]}",
        "{\"crearSesion\":true}",
        "{\"id\":99,\"sesionId\":88,\"titulo\":\"Acta ajena\"}"
    })
    void editarFueraDeAlcanceNoPuedeCambiarNingunaInformacion(String body) throws Exception {
        mvc.perform(get("/api/actas/20").with(user("ajeno@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Ver"))))
            .andExpect(status().isNotFound());
        mvc.perform(actualizacion(body).with(user("ajeno@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Crear", "Actas:Editar"))))
            .andExpect(status().isNotFound());
        sinCambios();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Actas:Crear", "Actas:Eliminar", "Actas:Ver"})
    void participanteSinEditarNoPuedeActualizar(String permiso) throws Exception {
        mvc.perform(actualizacion("{\"titulo\":\"Sin permiso\"}").with(user("participante@example.test")
                .authorities(autoridades("ROLE_miembro", permiso))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(actas);
        sinCambios();
    }

    @Test void participanteConEliminarPuedeEliminar() throws Exception {
        mvc.perform(delete("/api/actas/20").with(user("participante@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Eliminar"))))
            .andExpect(status().isNoContent());
        verify(actas).deleteById(20L);
        verify(participantes).deleteByActaId(20L);
        verify(acuerdos).deleteByActaId(20L);
        verify(firmas).deleteByActaId(20L);
        verify(adjuntos).deleteByActaId(20L);
        verify(auditoria).save(any());
    }

    @Test void eliminarConPermisoGeneralFueraDeAlcanceSeRechazaAntesDeTocarAdjuntos() throws Exception {
        mvc.perform(delete("/api/actas/20").with(user("ajeno@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Editar", "Actas:Eliminar"))))
            .andExpect(status().isNotFound());
        sinCambios();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Actas:Editar", "Actas:Crear", "Actas:Ver"})
    void participanteSinEliminarNoPuedeEliminar(String permiso) throws Exception {
        mvc.perform(delete("/api/actas/20").with(user("participante@example.test")
                .authorities(autoridades("ROLE_miembro", permiso))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(actas);
        sinCambios();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_superadmin", "ROLE_director", "ROLE_jefe de unidad"})
    void privilegioDeGestionConservaEdicionYEliminacionFueraDeParticipacion(String rol) throws Exception {
        var gestor = user("gestor@example.test").authorities(autoridades("ROLE_miembro", rol, "Actas:Editar", "Actas:Eliminar"));
        mvc.perform(actualizacion("{\"titulo\":\"Gestion autorizada\"}").with(gestor))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/actas/20").with(gestor)).andExpect(status().isNoContent());
        verify(actas).saveAndFlush(acta);
        verify(actas).deleteById(20L);
        verify(actas, never()).findByParticipanteUsuario(anyString(), anyString());
    }

    @Test void rolDeAdministradorSinPermisoFuncionalNoConcedeOperaciones() throws Exception {
        var administrador = user("gestor@example.test").authorities(autoridades("ROLE_administrador", "Actas:Ver"));
        mvc.perform(actualizacion("{}").with(administrador)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/actas/20").with(administrador)).andExpect(status().isForbidden());
        verifyNoInteractions(actas);
        sinCambios();
    }

    @Test void usuarioNoAutenticadoNoModificaEliminaNiSubeAdjuntos() throws Exception {
        mvc.perform(actualizacion("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/actas/20")).andExpect(status().isUnauthorized());
        mvc.perform(subida()).andExpect(status().isUnauthorized());
        verifyNoInteractions(actas);
        sinCambios();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Actas:Crear", "Actas:Editar", "Reuniones:Generar acta"})
    void subirAdjuntoConPermisoFueraDeAlcanceSigueRechazado(String permiso) throws Exception {
        mvc.perform(subida().with(user("ajeno@example.test").authorities(autoridades("ROLE_miembro", permiso))))
            .andExpect(status().isForbidden());
        sinCambios();
    }

    @Test void participanteSinPermisoDeSubidaNoPuedeAdjuntar() throws Exception {
        mvc.perform(subida().with(user("participante@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Ver"))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(actas);
        sinCambios();
    }

    @Test void actaInexistenteConPermisoResponde404SinCambios() throws Exception {
        var gestor = user("gestor@example.test").authorities(autoridades("ROLE_administrador", "Actas:Editar", "Actas:Eliminar"));
        mvc.perform(put("/api/actas/99").contentType(MediaType.APPLICATION_JSON).content("{}").with(gestor))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/actas/99").with(gestor)).andExpect(status().isNotFound());
        sinCambios();
    }

    @Test void autorizacionSeAplicaAlIdDeUrlAunqueElCuerpoIndiqueOtraActa() throws Exception {
        mvc.perform(actualizacion("{\"id\":99,\"titulo\":\"Actualizacion propia\"}")
                .with(user("participante@example.test").authorities(autoridades("ROLE_miembro", "Actas:Editar"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("20"));
        assertThat(acta.getId()).isEqualTo(20L);
        verify(actas).saveAndFlush(acta);
        verify(actas, never()).findById(99L);
    }

    @Test void editarPermiteSeleccionarResponsablesSinConcederAccesoGeneralAUsuarios() throws Exception {
        var editor = user("participante@example.test").authorities(autoridades("ROLE_miembro", "Actas:Editar"));
        mvc.perform(get("/api/usuarios/seleccionables").with(editor)).andExpect(status().isOk());
        verify(usuarioService).listarSeleccionables();
        mvc.perform(get("/api/usuarios").with(editor)).andExpect(status().isForbidden());
        verify(usuarioService, never()).listar();
    }

    @Test void eliminarSoloNoConcedeSeleccionDeResponsables() throws Exception {
        mvc.perform(get("/api/usuarios/seleccionables").with(user("participante@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Eliminar"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/seleccionables")).andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarioService);
    }

    private MockHttpServletRequestBuilder actualizacion(String body) {
        return put("/api/actas/20").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockMultipartHttpServletRequestBuilder subida() {
        return multipart("/api/actas/20/adjuntos")
            .file(new MockMultipartFile("file", "prueba.txt", "text/plain", "fixture sintetico".getBytes()));
    }

    private void sinCambios() {
        verify(actas, never()).saveAndFlush(any());
        verify(actas, never()).deleteById(anyLong());
        verifyNoInteractions(participantes, acuerdos, adjuntos, firmas, auditoria, sesiones, registros, invitados, tareas);
        assertThat(acta.getTitulo()).isEqualTo("Acta original");
        assertThat(acta.getEstado()).isEqualTo("Borrador");
        assertThat(acta.getSesion()).isNull();
    }

    private static SimpleGrantedAuthority[] autoridades(String... nombres) {
        return Arrays.stream(nombres).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
    }

    private static Usuario usuario(Long id, String correo, String nombre) {
        Usuario usuario = new Usuario(); usuario.setId(id); usuario.setCorreo(correo);
        usuario.setNombre(nombre); usuario.setApellido("Prueba"); return usuario;
    }
}
