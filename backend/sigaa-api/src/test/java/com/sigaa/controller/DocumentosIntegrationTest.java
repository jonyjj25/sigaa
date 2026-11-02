package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.GrupoService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired DocumentoRepository documentos;
    @Autowired CarpetaRepository carpetas;
    @Autowired RoleRepository roles;
    @Autowired GrupoRepository grupos;
    @Autowired UnidadRepository unidades;
    @Autowired GrupoService grupoService;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditLogRepository audit;
    @Autowired DocumentoController controller;
    @TempDir Path archivos;
    private Path uploadDirAnterior;
    private Usuario propietario;
    private Usuario otro;

    @BeforeEach void preparar() {
        propietario = usuario(); otro = usuario();
        uploadDirAnterior = (Path) ReflectionTestUtils.getField(controller, "uploadDir");
        ReflectionTestUtils.setField(controller, "uploadDir", archivos);
    }

    @AfterEach void limpiar() {
        reset(audit);
        ReflectionTestUtils.setField(controller, "uploadDir", uploadDirAnterior);
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().setStatisticsEnabled(false);
        for (Usuario usuario : List.of(propietario, otro)) {
            jdbc.update("DELETE FROM documentos WHERE propietario_id = ?", usuario.getId());
            jdbc.update("DELETE FROM carpetas WHERE propietario_id = ?", usuario.getId());
            jdbc.update("DELETE FROM notificaciones WHERE usuario_id = ?", usuario.getId());
            jdbc.update("DELETE FROM audit_logs WHERE usuario = ?", usuario.getCorreo());
            usuarios.deleteById(usuario.getId());
        }
    }

    @Test void creacionesSimultaneasDevuelvenElMismoDocumentoYUnaAuditoria() throws Exception {
        UUID clave = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch listos = new CountDownLatch(6), inicio = new CountDownLatch(1);
        try {
            List<Future<String>> respuestas = new ArrayList<>();
            for (int i = 0; i < 6; i++) respuestas.add(pool.submit(() -> {
                listos.countDown(); assertThat(inicio.await(15, TimeUnit.SECONDS)).isTrue();
                return crear(clave);
            }));
            assertThat(listos.await(15, TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            Set<String> ids = new HashSet<>();
            for (Future<String> respuesta : respuestas) ids.add(respuesta.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            Long id = Long.valueOf(ids.iterator().next());
            assertThat(documentos.findByPropietarioUsuarioIdAndCreacionId(propietario.getId(), clave)).isPresent();
            assertThat(audit.findByEntidadAndEntidadIdOrderByFechaDesc("documento", id)).filteredOn(a -> "CREAR".equals(a.getAccion())).hasSize(1);
        } finally { inicio.countDown(); pool.shutdownNow(); }
    }

    @Test void admiteMismoNombreEnOperacionesDistintasYRechazaReutilizarClaveConOtroContenido() throws Exception {
        UUID clave = UUID.randomUUID(); String primero = crear(clave);
        assertThat(crear(clave)).isEqualTo(primero);
        assertThat(crear(UUID.randomUUID())).isNotEqualTo(primero);
        mvc.perform(como(post("/api/documentos"), propietario, "Documentos:Crear")
            .contentType(MediaType.APPLICATION_JSON).content(body(clave).replace("Documento de prueba", "Otros datos"))).andExpect(status().isConflict());
    }

    @Test void falloDeAuditoriaRevierteLaCreacionYPermiteReintentar() throws Exception {
        UUID clave = UUID.randomUUID();
        doThrow(new IllegalStateException("fallo simulado")).when(audit).save(any(AuditLog.class));
        mvc.perform(como(post("/api/documentos"), propietario, "Documentos:Crear").contentType(MediaType.APPLICATION_JSON).content(body(clave))).andExpect(status().isConflict());
        assertThat(documentos.findByPropietarioUsuarioIdAndCreacionId(propietario.getId(), clave)).isEmpty();
        reset(audit);
        assertThat(crear(clave)).isNotBlank();
    }

    @Test void datosInvalidosNoCreanDocumentosNiApruebanPorEdicion() throws Exception {
        String valido = body(UUID.randomUUID());
        for (String invalido : List.of(valido.replace("Documento de prueba", " "), valido.replace("2026-10-05", "fecha-invalida"),
            valido.replace("borrador", "aprobado"), valido.replace("\"nombre\":\"Documento de prueba\"", "\"nombre\":123"),
            valido.replace("\"version\":\"1.0\"", "\"paginas\":null"), valido.replace("\"version\":\"1.0\"", "\"paginas\":4294967296"))) {
            mvc.perform(como(post("/api/documentos"), propietario, "Documentos:Crear").contentType(MediaType.APPLICATION_JSON).content(invalido)).andExpect(status().isBadRequest());
        }
        String id = crear(UUID.randomUUID());
        mvc.perform(como(put("/api/documentos/" + id), propietario, "Documentos:Editar").contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"aprobado\"}")).andExpect(status().isBadRequest());
        assertThat(documentos.findById(Long.valueOf(id)).orElseThrow().getEstado()).isEqualTo("borrador");
    }

    @Test void documentosPrivadosNoFiltranDatosNiPermitenCambiarAcceso() throws Exception {
        String id = crear(UUID.randomUUID());
        mvc.perform(como(get("/api/documentos"), otro, "Documentos:Ver")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
        mvc.perform(como(get("/api/documentos/" + id + "/descargar"), otro, "Documentos:Descargar")).andExpect(status().isNotFound());
        mvc.perform(como(get("/api/documentos/" + id + "/vista-previa"), otro, "Documentos:Ver")).andExpect(status().isNotFound());
        mvc.perform(como(put("/api/documentos/" + id), otro, "Documentos:Editar").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Otro\"}")).andExpect(status().isForbidden());
        mvc.perform(como(delete("/api/documentos/" + id), otro, "Documentos:Eliminar")).andExpect(status().isForbidden());
        mvc.perform(como(patch("/api/documentos/" + id + "/permiso"), otro, "ROLE_administrador").contentType(MediaType.APPLICATION_JSON).content("{\"nivel\":\"editar\"}")).andExpect(status().isForbidden());
    }

    @Test void aprobacionSoloPuedeResolverlaLaPersonaElegida() throws Exception {
        String id = crear(UUID.randomUUID());
        String archivo = mvc.perform(como(multipart("/api/documentos/" + id + "/archivo")
            .file(new MockMultipartFile("file", "revision.txt", "text/plain", "Contenido de prueba".getBytes(java.nio.charset.StandardCharsets.UTF_8))), propietario, "Documentos:Editar"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number versionId = JsonPath.read(archivo, "$.versionActualId");
        String respuesta = mvc.perform(como(patch("/api/documentos/" + id + "/solicitar-aprobacion"), propietario, "Documentos:Ver")
            .contentType(MediaType.APPLICATION_JSON).content("{\"aprobadorId\":" + otro.getId() + ",\"versionId\":" + versionId + "}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number solicitudId = JsonPath.read(respuesta, "$.solicitudAprobacion.id");
        String decision = "{\"decision\":\"aprobar\",\"solicitudId\":" + solicitudId + ",\"versionId\":" + versionId + "}";
        mvc.perform(como(patch("/api/documentos/" + id + "/resolver-aprobacion"), propietario, "Documentos:Ver")
            .contentType(MediaType.APPLICATION_JSON).content(decision)).andExpect(status().isForbidden());
        mvc.perform(como(get("/api/documentos"), otro, "Documentos:Ver")).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + id + "')].solicitudAprobacion.aprobadorId").value(org.hamcrest.Matchers.hasItem(otro.getId().intValue())));
        mvc.perform(como(patch("/api/documentos/" + id + "/resolver-aprobacion"), otro, "Documentos:Ver")
            .contentType(MediaType.APPLICATION_JSON).content(decision)).andExpect(status().isOk());
        mvc.perform(como(patch("/api/documentos/" + id + "/resolver-aprobacion"), otro, "Documentos:Ver")
            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"aprobar\"}")).andExpect(status().isForbidden());
        assertThat(documentos.findById(Long.valueOf(id)).orElseThrow().getEstado()).isEqualTo("aprobado");
    }

    @Test void editorCompartidoPuedeEditarPeroNoAdquirirMasAccesoMoviendoElDocumento() throws Exception {
        String id = crear(UUID.randomUUID());
        otro.setRoles(Set.of(roles.findByNombre("Administrador").orElseThrow()));
        otro = usuarios.saveAndFlush(otro);
        mvc.perform(como(post("/api/documentos/" + id + "/compartidos"), propietario, "Documentos:Ver")
            .contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":" + otro.getId() + ",\"permiso\":\"EDICION\"}")).andExpect(status().isOk());
        mvc.perform(como(put("/api/documentos/" + id), otro, "Documentos:Editar")
            .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Edición permitida\"}")).andExpect(status().isOk());
        Carpeta carpeta = new Carpeta(); carpeta.setNombre("Carpeta del editor"); carpeta.setPropietario(otro); carpeta = carpetas.saveAndFlush(carpeta);
        for (String cambio : List.of("{\"carpetaId\":" + carpeta.getId() + "}", "{\"permiso\":\"administrar\"}", "{\"clasificacion\":\"PUBLICO\"}")) {
            mvc.perform(como(put("/api/documentos/" + id), otro, "Documentos:Editar")
                .contentType(MediaType.APPLICATION_JSON).content(cambio)).andExpect(status().isForbidden());
        }
        Documento documento = documentos.findById(Long.valueOf(id)).orElseThrow();
        assertThat(documento.getCarpetaId()).isNull();
        assertThat(documento.getClasificacion()).isEqualTo(DocumentoClasificacion.CONFIDENCIAL);
        assertThat(documento.getDescripcion()).isEqualTo("Edición permitida");
    }

    @Test void cargaDeListadoNoAumentaConsultasPorDocumentoNiRecalculaArchivos() throws Exception {
        for (int i = 0; i < 5; i++) documentoPublico();
        var estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true); estadisticas.clear();
        mvc.perform(como(get("/api/documentos"), propietario, "Documentos:Ver")).andExpect(status().isOk());
        long consultasIniciales = estadisticas.getPrepareStatementCount();
        for (int i = 0; i < 20; i++) documentoPublico();
        estadisticas.clear();
        mvc.perform(como(get("/api/documentos"), propietario, "Documentos:Ver")).andExpect(status().isOk());
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(consultasIniciales);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Consultas del listado con 5 y 25 documentos: {} y {}", consultasIniciales, estadisticas.getPrepareStatementCount());
        assertThat(estadisticas.getEntityUpdateCount()).isZero();
    }

    @Test void carpetasRespetanVisibilidadYConteosSinRepetirDocumentos() throws Exception {
        Carpeta carpeta = new Carpeta(); carpeta.setNombre("Prueba"); carpeta.setPropietario(propietario); carpeta = carpetas.saveAndFlush(carpeta);
        Documento doc = documentos.findById(Long.valueOf(crear(UUID.randomUUID()))).orElseThrow();
        doc.setCarpetaId(carpeta.getId()); documentos.saveAndFlush(doc);
        mvc.perform(como(get("/api/documentos/carpetas").param("incluirDocumentos", "false"), propietario, "Documentos:Ver"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + carpeta.getId() + "')].count").value(org.hamcrest.Matchers.hasItem(1)))
            .andExpect(jsonPath("$[?(@.id == '" + carpeta.getId() + "')].documentos[0]").isEmpty());
        mvc.perform(como(get("/api/documentos/carpetas"), otro, "Documentos:Ver")).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + carpeta.getId() + "')]").isEmpty());
    }

    @Test void propietarioPuedeEliminarSuCarpetaCompartidaPeroOtroUsuarioNo() throws Exception {
        Carpeta carpeta = new Carpeta(); carpeta.setNombre("Equipo"); carpeta.setTipo("COMPARTIDA");
        carpeta.setPropietario(propietario); carpeta = carpetas.saveAndFlush(carpeta);
        mvc.perform(como(get("/api/documentos/carpetas").param("incluirDocumentos", "false"), propietario, "Documentos:Ver"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + carpeta.getId() + "')].puedeEliminar").value(org.hamcrest.Matchers.hasItem(true)));
        mvc.perform(como(delete("/api/documentos/carpetas/" + carpeta.getId()), otro, "Documentos:Crear carpeta"))
            .andExpect(status().isForbidden());
        mvc.perform(como(delete("/api/documentos/carpetas/" + carpeta.getId()), propietario, "Documentos:Crear carpeta"))
            .andExpect(status().isNoContent());
        assertThat(carpetas.findById(carpeta.getId())).isEmpty();
    }

    @Test void reemplazosSimultaneosNoDejanArchivosHuerfanos() throws Exception { probarCarreraArchivo("reemplazar"); }
    @Test void editarDuranteUnReemplazoConservaLaRutaNueva() throws Exception { probarCarreraArchivo("editar"); }
    @Test void eliminarDuranteUnReemplazoConservaElHistorialConfirmado() throws Exception { probarCarreraArchivo("eliminar"); }
    @Test void eliminarCarpetaDuranteUnReemplazoConservaElHistorialConfirmado() throws Exception { probarCarreraArchivo("eliminarCarpeta"); }

    @Test void documentosDeGruposFiltranUnidadYPermisosSinConsultasPorDocumento() throws Exception {
        Unidad unidad = new Unidad(); unidad.setNombre("Unidad docs-test-" + UUID.randomUUID()); unidad = unidades.saveAndFlush(unidad);
        Grupo grupo = new Grupo(); grupo.setNombre("Grupo de prueba"); grupo.setTipo("Unidad"); grupo.setUnidad(unidad);
        GrupoUsuario miembro = new GrupoUsuario(); miembro.setGrupo(grupo); miembro.setUsuario(propietario); grupo.getMiembros().add(miembro);
        grupo = grupos.saveAndFlush(grupo);
        try {
            String area = unidad.getNombre().toUpperCase(Locale.ROOT);
            Documento privado = documentoGrupo(area, DocumentoClasificacion.CONFIDENCIAL);
            Documento ajeno = documentoGrupo("Otra unidad", DocumentoClasificacion.PUBLICO);
            for (int i = 0; i < 5; i++) documentoGrupo(area, DocumentoClasificacion.PUBLICO);
            assertThat(documentos.findByAreaIgnoreCaseOrderByFechaDesc(unidad.getNombre())).extracting(Documento::getId)
                .contains(privado.getId()).doesNotContain(ajeno.getId());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(propietario.getCorreo(), "", List.of()));
            var estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            estadisticas.setStatisticsEnabled(true); estadisticas.clear();
            assertThat(grupoService.archivos(grupo.getId())).hasSize(5).allSatisfy(d -> assertThat(d.get("id")).isNotEqualTo(privado.getId()));
            long consultas = estadisticas.getPrepareStatementCount();
            for (int i = 0; i < 20; i++) documentoGrupo(area, DocumentoClasificacion.PUBLICO);
            estadisticas.clear();
            assertThat(grupoService.archivos(grupo.getId())).hasSize(25);
            assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(consultas);
            assertThat(grupoService.resumen(grupo.getId()).get("archivos")).isEqualTo(25L);
            org.slf4j.LoggerFactory.getLogger(getClass()).info("Consultas de archivos de Grupos con 5 y 25 visibles: {} y {}", consultas, consultas);
        } finally { SecurityContextHolder.clearContext(); grupos.deleteById(grupo.getId()); unidades.deleteById(unidad.getId()); }
    }

    private Documento documentoGrupo(String area, DocumentoClasificacion clasificacion) {
        Documento doc = new Documento(); doc.setNombre("Documento del grupo"); doc.setArea(area); doc.setClasificacion(clasificacion);
        doc.setFecha(LocalDate.now()); doc.setPropietarioUsuarioId(otro.getId()); return documentos.saveAndFlush(doc);
    }

    private void probarCarreraArchivo(String operacion) throws Exception {
        Long id = Long.valueOf(crear(UUID.randomUUID()));
        Documento documento = documentos.findById(id).orElseThrow();
        Path original = Files.writeString(archivos.resolve("original.txt"), "original");
        documento.setArchivoRuta(original.toString());
        Carpeta carpeta = new Carpeta();
        if ("eliminarCarpeta".equals(operacion)) {
            carpeta.setNombre("Carpeta concurrente"); carpeta.setPropietario(propietario); carpeta = carpetas.saveAndFlush(carpeta);
            documento.setCarpetaId(carpeta.getId());
        }
        documentos.saveAndFlush(documento);
        Long carpetaId = carpeta.getId();
        CountDownLatch primeraGuardada = new CountDownLatch(1), confirmarPrimera = new CountDownLatch(1), segundaIniciada = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean primera = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocacion -> {
            AuditLog log = invocacion.getArgument(0);
            if ("ACTUALIZAR_ARCHIVO".equals(log.getAccion()) && id.equals(log.getEntidadId()) && primera.getAndSet(false)) {
                primeraGuardada.countDown();
                assertThat(confirmarPrimera.await(15, TimeUnit.SECONDS)).isTrue();
            }
            entityManager.persist(log);
            return log;
        }).when(audit).save(any(AuditLog.class));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> primero = pool.submit(() -> mvc.perform(como(multipart("/api/documentos/" + id + "/archivo")
                .file(new MockMultipartFile("file", "primero.txt", "text/plain", "primero".getBytes(java.nio.charset.StandardCharsets.UTF_8))), propietario, "Documentos:Editar")).andExpect(status().isOk()));
            assertThat(primeraGuardada.await(15, TimeUnit.SECONDS)).isTrue();
            Future<?> segundo = pool.submit(() -> {
                segundaIniciada.countDown();
                return switch (operacion) {
                    case "reemplazar" -> mvc.perform(como(multipart("/api/documentos/" + id + "/archivo")
                        .file(new MockMultipartFile("file", "segundo.txt", "text/plain", "segundo".getBytes(java.nio.charset.StandardCharsets.UTF_8))), propietario, "Documentos:Editar")).andExpect(status().isOk());
                    case "editar" -> mvc.perform(como(put("/api/documentos/" + id), propietario, "Documentos:Editar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Edición concurrente\"}")).andExpect(status().isOk());
                    case "eliminar" -> mvc.perform(como(delete("/api/documentos/" + id), propietario, "Documentos:Eliminar")).andExpect(status().isNoContent());
                    case "eliminarCarpeta" -> mvc.perform(como(delete("/api/documentos/carpetas/" + carpetaId), propietario, "Documentos:Crear carpeta")).andExpect(status().isNoContent());
                    default -> throw new IllegalArgumentException(operacion);
                };
            });
            assertThat(segundaIniciada.await(15, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> segundo.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(original).exists();
            confirmarPrimera.countDown();
            primero.get(15, TimeUnit.SECONDS); segundo.get(15, TimeUnit.SECONDS);
            assertThat(original).exists();
            try (var restantes = Files.list(archivos)) {
                if (operacion.startsWith("eliminar")) {
                    assertThat(documentos.findById(id)).isEmpty(); assertThat(restantes.toList()).hasSize(2).contains(original);
                } else {
                    Documento guardado = documentos.findById(id).orElseThrow();
                    Path vigente = Path.of(guardado.getArchivoRuta());
                    assertThat(restantes.toList()).hasSize("reemplazar".equals(operacion) ? 3 : 2).contains(original, vigente);
                    assertThat(Files.readString(vigente)).isEqualTo("reemplazar".equals(operacion) ? "segundo" : "primero");
                    if ("editar".equals(operacion)) assertThat(guardado.getDescripcion()).isEqualTo("Edición concurrente");
                }
            }
        } finally { confirmarPrimera.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
    }

    private Usuario usuario() {
        Usuario u = new Usuario(); String clave = "docs-test-" + UUID.randomUUID();
        u.setNombre("Usuario de prueba"); u.setCorreo(clave + "@example.test"); u.setUsername(clave); u.setPassword("password-de-prueba");
        return usuarios.saveAndFlush(u);
    }

    private void documentoPublico() {
        Documento d = new Documento(); d.setNombre("Carga de prueba"); d.setFecha(LocalDate.now()); d.setPropietarioUsuarioId(otro.getId());
        d.setClasificacion(DocumentoClasificacion.PUBLICO); d.setPaginas(0); d.setArchivoRuta("uploads/documentos/prueba-inexistente.docx"); documentos.saveAndFlush(d);
    }

    private String crear(UUID clave) throws Exception {
        String respuesta = mvc.perform(como(post("/api/documentos"), propietario, "Documentos:Crear").contentType(MediaType.APPLICATION_JSON).content(body(clave)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    private String body(UUID clave) {
        return "{\"nombre\":\"Documento de prueba\",\"area\":\"Personal\",\"fecha\":\"2026-10-05\",\"estado\":\"borrador\",\"version\":\"1.0\",\"clasificacion\":\"CONFIDENCIAL\",\"creacionId\":\"" + clave + "\"}";
    }

    private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder request, Usuario usuario, String permiso) {
        return request.with(user(usuario.getCorreo()).authorities(new SimpleGrantedAuthority(permiso)));
    }

    private MockMultipartHttpServletRequestBuilder como(MockMultipartHttpServletRequestBuilder request, Usuario usuario, String permiso) {
        return request.with(user(usuario.getCorreo()).authorities(new SimpleGrantedAuthority(permiso)));
    }
}
