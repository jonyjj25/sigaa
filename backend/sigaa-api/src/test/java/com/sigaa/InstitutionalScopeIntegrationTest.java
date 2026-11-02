package com.sigaa;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Todas las cuentas y recursos son ficticios en el clúster efímero de seguridad. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InstitutionalScopeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permisos;
    @Autowired UsuarioDetailsService details;
    @Autowired ActaRepository actas;
    @Autowired AsistenciaSesionRepository sesiones;
    @Autowired TareaRepository tareas;
    @Autowired ReporteRepository reportes;
    @Autowired ReunionRepository reuniones;
    @Autowired InventarioActivoRepository activos;
    @Autowired GrupoRepository grupos;
    @Autowired UnidadRepository unidades;
    @Autowired UsuarioUnidadRepository membresias;
    @Autowired DocumentoAccessService documentos;
    @Autowired RoleAdministrationService administracion;
    @Autowired ReporteGenerationService generacion;

    private Usuario cuenta(String role) {
        Usuario u = new Usuario();
        String id = UUID.randomUUID().toString();
        u.setNombre("Usuario de prueba"); u.setApellido(id.substring(0, 8));
        u.setCorreo(id + "@example.test"); u.setUsername("test-" + id);
        u.setPassword("TEST_ONLY_NOT_REAL"); u.setActivo(true); u.setBloqueado(false);
        u.setRoles(Set.of(roles.findByNombre(role).orElseThrow()));
        return usuarios.saveAndFlush(u);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor autenticado(Usuario u) {
        return user(details.loadUserByUsername(u.getCorreo()));
    }

    private void contexto(Usuario u) {
        var principal = details.loadUserByUsername(u.getCorreo());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Acta acta(Usuario responsable) {
        AsistenciaSesion sesion = new AsistenciaSesion();
        sesion.setCodigo(UUID.randomUUID().toString()); sesion.setNombre("Sesión ficticia");
        sesion.setFecha(LocalDate.now()); sesion.setCreadoPor(responsable.getId());
        sesion = sesiones.saveAndFlush(sesion);
        Acta a = new Acta(); a.setCodigo("TEST-" + UUID.randomUUID()); a.setTitulo("Acta ficticia");
        a.setFecha(LocalDate.now()); a.setHora(LocalTime.NOON); a.setLugar("Lugar ficticio");
        a.setTipo("Ordinaria"); a.setEstado("Borrador"); a.setResponsable(responsable.getCorreo());
        a.setSesion(sesion);
        return actas.saveAndFlush(a);
    }

    @Test void retiredAdministratorIsPreservedButCannotGrantOrReceiveAccess() throws Exception {
        Role retirado = roles.findByNombre("Administrador").orElseThrow();
        assertFalse(retirado.getActivo());
        Usuario u = cuenta("Administrador");
        // Ni siquiera una fila histórica accidentalmente activa reactiva este rol.
        retirado.setActivo(true); roles.saveAndFlush(retirado);
        var authorityNames = details.loadUserByUsername(u.getCorreo()).getAuthorities();
        assertTrue(authorityNames.isEmpty());
        assertTrue(usuarios.findById(u.getId()).orElseThrow().getRoles().contains(retirado));
        mvc.perform(get("/api/roles").with(autenticado(u))).andExpect(status().isForbidden());
        Usuario superadmin = cuenta("Superadmin");
        mvc.perform(get("/api/roles").with(autenticado(superadmin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.nombre == 'Administrador')]").isEmpty());
        var request = new com.sigaa.dto.RoleRequest();
        request.setNombre(" administrador "); request.setActivo(true);
        assertThrows(IllegalStateException.class, () -> administracion.create(request, "prueba", "127.0.0.1"));
        assertFalse(InstitutionalRoles.global(retirado.getNombre()));
    }

    @Test void superadminUsesTheCurrentCatalogInsteadOfOnlyItsHistoricalMatrix() throws Exception {
        Usuario superadmin = cuenta("Superadmin");
        Permission nuevo = new Permission(); nuevo.setModulo("Modulo de prueba"); nuevo.setAccion("Editar");
        nuevo.setRecurso("Prueba"); nuevo.setActivo(true); nuevo = permisos.saveAndFlush(nuevo);
        assertTrue(details.loadUserByUsername(superadmin.getCorreo()).getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("Modulo de prueba:Editar")));
        nuevo.setActivo(false); permisos.saveAndFlush(nuevo);
        assertFalse(details.loadUserByUsername(superadmin.getCorreo()).getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("Modulo de prueba:Editar")));
        mvc.perform(get("/api/roles").with(autenticado(superadmin))).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"Director", "Subdirector"})
    void leadershipDoesNotReceiveTechnicalAdministration(String role) throws Exception {
        Usuario u = cuenta(role);
        var authorities = details.loadUserByUsername(u.getCorreo()).getAuthorities();
        assertFalse(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_administrador")));
        mvc.perform(get("/api/roles").with(autenticado(u))).andExpect(status().isForbidden());
        mvc.perform(get("/api/security/policies").with(autenticado(u))).andExpect(status().isForbidden());
    }

    @Test void directionAndSubdirectionPermissionEditsAreIndependent() {
        Role director = roles.findByNombre("Director").orElseThrow();
        Role subdirector = roles.findByNombre("Subdirector").orElseThrow();
        Set<Permission> anteriores = new HashSet<>(subdirector.getPermisos());
        Permission lectura = permisos.findByActivoTrue().stream()
            .filter(p -> "Actas".equals(p.getModulo()) && "Ver".equals(p.getAccion())).findFirst().orElseThrow();
        administracion.assignPermissions(director.getId(), Set.of(lectura.getId()), "prueba", "127.0.0.1");
        assertEquals(anteriores, subdirector.getPermisos());
    }

    @Test void subdirectorCanReadEditAndDeleteOnlyItsOwnActa() throws Exception {
        Usuario sub = cuenta("Subdirector"); Usuario otro = cuenta("Miembro");
        Acta propia = acta(sub); Acta ajena = acta(otro);
        mvc.perform(get("/api/actas/{id}", propia.getId()).with(autenticado(sub))).andExpect(status().isOk());
        mvc.perform(get("/api/actas/{id}", ajena.getId()).with(autenticado(sub))).andExpect(status().isNotFound());
        mvc.perform(put("/api/actas/{id}", ajena.getId()).with(autenticado(sub))
            .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Cambio no autorizado\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/actas/{id}", ajena.getId()).with(autenticado(sub))).andExpect(status().isNotFound());
        assertTrue(actas.existsById(ajena.getId()));
        mvc.perform(put("/api/actas/{id}", propia.getId()).with(autenticado(sub))
            .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Actualizada\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/actas/{id}", propia.getId()).with(autenticado(sub))).andExpect(status().isNoContent());
    }

    @ParameterizedTest @ValueSource(strings = {"Director", "Superadmin"})
    void globalOperationalRolesCanReachOtherActasAndDocuments(String role) throws Exception {
        Usuario gestor = cuenta(role); Acta ajena = acta(cuenta("Miembro"));
        mvc.perform(get("/api/actas/{id}", ajena.getId()).with(autenticado(gestor))).andExpect(status().isOk());
        mvc.perform(delete("/api/actas/{id}", ajena.getId()).with(autenticado(gestor))).andExpect(status().isNoContent());
        Documento documento = new Documento(); documento.setId(100L); documento.setPropietarioUsuarioId(999L);
        documento.setClasificacion(DocumentoClasificacion.CONFIDENCIAL);
        assertTrue(documentos.canView(documento, gestor)); assertTrue(documentos.canModify(documento, gestor));
        assertTrue(documentos.canManageAccess(documento, gestor));
    }

    @Test void resourceScopeDoesNotReplaceFunctionalPermissionOrAuthentication() throws Exception {
        Usuario sub = cuenta("Subdirector"); Acta propia = acta(sub);
        mvc.perform(delete("/api/actas/{id}", propia.getId())
            .with(user(sub.getCorreo()).roles("Subdirector"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/actas/{id}", propia.getId())).andExpect(status().isUnauthorized());
        assertTrue(actas.existsById(propia.getId()));
    }

    @Test void scopedReportGenerationDoesNotCopyUnrelatedRows() {
        Usuario sub = cuenta("Subdirector"); Acta propia = acta(sub); Acta ajena = acta(cuenta("Miembro"));
        contexto(sub);
        try {
            Reporte reporte = generacion.generar("Prueba", "Actas", "ejecutivo", "CSV",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), sub.getCorreo());
            assertEquals(sub.getId(), reporte.getCreadoPorUsuarioId());
            assertTrue(reporte.getContenido().contains(propia.getCodigo()));
            assertFalse(reporte.getContenido().contains(ajena.getCodigo()));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void reportOwnerIsServerIdentityAndLegacyOrForeignReportsRemainOutsideSubdirection() throws Exception {
        Usuario sub = cuenta("Subdirector"); Usuario otro = cuenta("Miembro");
        Reporte propio = reporte(sub.getId()); Reporte ajeno = reporte(otro.getId()); Reporte historico = reporte(null);
        // El texto editable nunca convierte un reporte ajeno en propio.
        ajeno.setGeneradoPor(sub.getCorreo()); reportes.saveAndFlush(ajeno);
        mvc.perform(get("/api/reportes/{id}", propio.getId()).with(autenticado(sub))).andExpect(status().isOk());
        for (Reporte fuera : List.of(ajeno, historico)) {
            mvc.perform(get("/api/reportes/{id}", fuera.getId()).with(autenticado(sub))).andExpect(status().isNotFound());
            mvc.perform(delete("/api/reportes/{id}", fuera.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
            mvc.perform(get("/api/reportes/{id}/exportar", fuera.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
            mvc.perform(get("/api/reportes/{id}/descargar-archivo/0", fuera.getId()).with(autenticado(sub)))
                .andExpect(status().isForbidden());
        }
    }

    private Reporte reporte(Long propietario) {
        Reporte r = new Reporte(); r.setNombre("Reporte ficticio"); r.setModulo("Actas"); r.setFecha(LocalDate.now());
        r.setCreadoPorUsuarioId(propietario); return reportes.saveAndFlush(r);
    }

    @Test void subdirectionCannotUseGeneralTaskPermissionToDeleteUnrelatedTasks() throws Exception {
        Usuario sub = cuenta("Subdirector"); Usuario otro = cuenta("Miembro");
        Tarea propia = tarea(sub); Tarea ajena = tarea(otro);
        mvc.perform(get("/api/tareas/{id}", propia.getId()).with(autenticado(sub))).andExpect(status().isOk());
        mvc.perform(get("/api/tareas/{id}", ajena.getId()).with(autenticado(sub))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/tareas/{id}", ajena.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tareas/{id}", propia.getId()).with(autenticado(sub))).andExpect(status().isNoContent());
        assertTrue(tareas.existsById(ajena.getId()));
    }

    private Tarea tarea(Usuario asignado) {
        Tarea t = new Tarea(); t.setCodigo("TASK-" + UUID.randomUUID()); t.setTitulo("Tarea ficticia");
        t.setFechaCreacion(LocalDate.now()); t.setAsignadoUsuario(asignado);
        return tareas.saveAndFlush(t);
    }

    @Test void subdirectionCanManageOrganizedMeetingsButCannotReachForeignIds() throws Exception {
        Usuario sub = cuenta("Subdirector"); Usuario otro = cuenta("Miembro");
        Reunion propia = reunion(sub); Reunion ajena = reunion(otro);
        mvc.perform(get("/api/reuniones/{id}", propia.getId()).with(autenticado(sub))).andExpect(status().isOk());
        mvc.perform(get("/api/reuniones/{id}", ajena.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/reuniones/{id}", ajena.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/reuniones/{id}", propia.getId()).with(autenticado(sub))).andExpect(status().isNoContent());
        assertTrue(reuniones.existsById(ajena.getId()));
    }

    private Reunion reunion(Usuario organizador) {
        Reunion r = new Reunion(); r.setCodigo("RUN-" + UUID.randomUUID().toString().substring(0, 20));
        r.setTitulo("Reunión ficticia"); r.setFecha(LocalDate.now());
        r.setHoraInicio(LocalTime.NOON); r.setHoraFin(LocalTime.of(13, 0)); r.setOrganizadorUsuario(organizador);
        return reuniones.saveAndFlush(r);
    }

    @Test void inventoryListAssignmentsAndChangesRespectSubdirectionScope() throws Exception {
        Usuario sub = cuenta("Subdirector"); Usuario otro = cuenta("Miembro");
        InventarioActivo propio = activo(sub); InventarioActivo ajeno = activo(otro);
        mvc.perform(get("/api/inventario").with(autenticado(sub)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.codigo == '" + propio.getCodigo() + "')]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.codigo == '" + ajeno.getCodigo() + "')]").isEmpty());
        String ficha = "{\"nombre\":\"Activo ficticio\",\"categoria\":\"Equipo\",\"ubicacion\":\"Oficina ficticia\","
            + "\"estado\":\"en buen estado\",\"tipoAsignacion\":\"PERSONA\",\"asignadoUsuarioId\":" + sub.getId()
            + ",\"lockVersion\":0}";
        mvc.perform(put("/api/inventario/{id}", ajeno.getId()).with(autenticado(sub))
            .contentType(MediaType.APPLICATION_JSON).content(ficha)).andExpect(status().isForbidden());
        mvc.perform(put("/api/inventario/{id}", propio.getId()).with(autenticado(sub))
            .contentType(MediaType.APPLICATION_JSON).content(ficha)).andExpect(status().isOk());
        mvc.perform(get("/api/inventario/asignables").with(autenticado(sub)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.personas.length()").value(1))
            .andExpect(jsonPath("$.personas[0].id").value(sub.getId()));
        mvc.perform(delete("/api/inventario/{id}", ajeno.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/inventario/{id}", propio.getId()).with(autenticado(sub))).andExpect(status().isNoContent());
    }

    private InventarioActivo activo(Usuario propietario) {
        InventarioActivo a = new InventarioActivo(); a.setCodigo("INV-" + UUID.randomUUID());
        a.setNombre("Activo ficticio"); a.setAsignadoUsuario(propietario);
        return activos.saveAndFlush(a);
    }

    @Test void groupsRequireARealRelationshipForSubdirection() throws Exception {
        Usuario sub = cuenta("Subdirector"); Usuario otro = cuenta("Miembro");
        Unidad unidad = new Unidad(); unidad.setNombre("Unidad ficticia " + UUID.randomUUID());
        unidad = unidades.saveAndFlush(unidad);
        Grupo propio = grupo(sub, unidad); Grupo ajeno = grupo(otro, unidad);
        mvc.perform(get("/api/grupos/{id}", propio.getId()).with(autenticado(sub))).andExpect(status().isOk());
        mvc.perform(get("/api/grupos/{id}", ajeno.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/grupos/{id}", ajeno.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/grupos/{id}", propio.getId()).with(autenticado(sub))).andExpect(status().isNoContent());
    }

    private Grupo grupo(Usuario creador, Unidad unidad) {
        Grupo g = new Grupo(); g.setNombre("Grupo ficticio " + UUID.randomUUID());
        g.setUnidad(unidad); g.setTipo("Comité"); g.setCreadoPor(creador);
        return grupos.saveAndFlush(g);
    }

    @Test void inactiveUnitMembershipCannotExpandSubdirectionScope() throws Exception {
        Usuario sub = cuenta("Subdirector"); Usuario otro = cuenta("Miembro");
        Unidad unidad = new Unidad(); unidad.setNombre("Ámbito ficticio " + UUID.randomUUID());
        unidad = unidades.saveAndFlush(unidad);
        Role rolInactivo = new Role("Rol de unidad inactivo " + UUID.randomUUID(), "Prueba");
        rolInactivo.setActivo(false); rolInactivo = roles.saveAndFlush(rolInactivo);
        UsuarioUnidad relacion = new UsuarioUnidad(); relacion.setUsuario(sub); relacion.setUnidad(unidad);
        relacion.setRol(rolInactivo); membresias.saveAndFlush(relacion);
        Tarea tarea = tarea(otro); tarea.setUnidades(unidad.getNombre()); tareas.saveAndFlush(tarea);
        Reunion reunion = reunion(otro); reunion.setUnidadResponsable(unidad.getNombre()); reuniones.saveAndFlush(reunion);
        Grupo grupo = grupo(otro, unidad);
        mvc.perform(get("/api/tareas/{id}", tarea.getId()).with(autenticado(sub))).andExpect(status().isNotFound());
        mvc.perform(get("/api/reuniones/{id}", reunion.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
        mvc.perform(get("/api/grupos/{id}", grupo.getId()).with(autenticado(sub))).andExpect(status().isForbidden());
    }
}
