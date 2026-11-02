package com.sigaa.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentoVersionesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DocumentoController controller;
    @Autowired DocumentoRepository documentos;
    @Autowired DocumentoVersionRepository versiones;
    @Autowired DocumentoSolicitudAprobacionRepository solicitudes;
    @Autowired UsuarioRepository usuarios;
    @Autowired RoleRepository roles;
    @Autowired CarpetaRepository carpetas;
    @Autowired CarpetaUsuarioRepository miembros;
    @Autowired UnidadRepository unidades;
    @Autowired UsuarioUnidadRepository membresias;
    @Autowired DocumentoUsuarioAccesoRepository accesos;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditLogRepository audit;
    @TempDir Path archivos;
    private Path directorioAnterior;
    private Usuario propietario, revisor, ajeno;
    private final List<Long> userIds = new ArrayList<>(), docIds = new ArrayList<>(), folderIds = new ArrayList<>();
    private final List<Long> unidadIds = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void preparar() {
        propietario = usuario("Propietario", "Miembro"); revisor = usuario("Revisor", "Miembro"); ajeno = usuario("Ajeno", "Miembro");
        directorioAnterior = (Path) ReflectionTestUtils.getField(controller, "uploadDir");
        ReflectionTestUtils.setField(controller, "uploadDir", archivos);
    }

    @AfterEach void limpiar() {
        reset(audit); ReflectionTestUtils.setField(controller, "uploadDir", directorioAnterior);
        for (Long id : docIds) {
            jdbc.update("delete from audit_logs where entidad = 'documento' and entidad_id = ?", id);
            jdbc.update("delete from documentos where id = ?", id);
        }
        for (Long id : folderIds) {
            jdbc.update("delete from audit_logs where entidad = 'carpeta_documento' and entidad_id = ?", id);
            jdbc.update("delete from carpetas where id = ?", id);
        }
        for (Long id : userIds) { jdbc.update("delete from notificaciones where usuario_id = ?", id); jdbc.update("delete from usuario_unidad where usuario_id = ?", id); usuarios.deleteById(id); }
        for (Long id : unidadIds) unidades.deleteById(id);
    }

    @Test void borradorSinArchivoNoTieneVersionDeContenido() throws Exception {
        long id = crear();
        assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).isEmpty();
        mvc.perform(get("/api/documentos").with(actor(propietario))).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + id + "')].version", org.hamcrest.Matchers.hasItem("Sin archivo")));
        JsonNode historial = historial(id);
        assertThat(historial.path("versiones").size()).isZero();
        assertThat(historial.path("archivoActualDisponible").asBoolean()).isFalse();
    }

    @Test void primeraVersionConservaAutorFechaHoraYArchivo() throws Exception {
        long id = crear(); JsonNode respuesta = subir(id, "contenido inicial");
        assertThat(respuesta.path("version").asText()).isEqualTo("1.0");
        DocumentoVersion version = versiones.findById(respuesta.path("versionActualId").asLong()).orElseThrow();
        assertThat(version.getAutorId()).isEqualTo(propietario.getId());
        assertThat(version.getRegistradaEn()).isNotNull(); assertThat(version.getContenidoHash()).hasSize(64);
        assertThat(Files.readString(Path.of(version.getArchivoRuta()))).isEqualTo("contenido inicial");
        assertThat(respuesta.toString()).doesNotContain(archivos.toString()).doesNotContain("archivo_ruta");
    }

    @Test void reemplazosSucesivosConservanTodasLasVersionesYDescargas() throws Exception {
        long id = crear(); subir(id, "primero"); subir(id, "segundo"); subir(id, "tercero");
        List<DocumentoVersion> historial = versiones.findByDocumentoIdOrderByIdDesc(id);
        assertThat(historial).extracting(DocumentoVersion::getVersion).containsExactly("3.0", "2.0", "1.0");
        for (DocumentoVersion version : historial) {
            String esperado = switch (version.getNumero()) { case 1 -> "primero"; case 2 -> "segundo"; default -> "tercero"; };
            mvc.perform(get("/api/documentos/{id}/versiones/{versionId}/descargar", id, version.getId()).with(actor(propietario)))
                .andExpect(status().isOk()).andExpect(content().string(esperado));
        }
        assertThat(numeroArchivos()).isEqualTo(3);
    }

    @Test void contenidoIdenticoNoIncrementaNiCambiaArchivoAunqueCambieNombre() throws Exception {
        long id = crear(); JsonNode inicial = subir(id, "igual");
        var respuesta = mvc.perform(multipart("/api/documentos/{id}/archivo", id).file(archivo("otro.txt", "igual")).with(actor(propietario)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(respuesta).path("contenidoIdentico").asBoolean()).isTrue();
        assertThat(json.readTree(respuesta).path("versionActualId")).isEqualTo(inicial.path("versionActualId"));
        assertThat(numeroArchivos()).isEqualTo(1); assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).hasSize(1);
    }

    @Test void unaCopiaIdenticaRecuperaUnArchivoAusenteSinInventarOtraVersion() throws Exception {
        long id = crear(); long version = subir(id, "contenido verificable").path("versionActualId").asLong();
        DocumentoVersion anterior = versiones.findById(version).orElseThrow();
        Files.delete(Path.of(anterior.getArchivoRuta()));
        JsonNode recuperada = subir(id, "contenido verificable");
        assertThat(recuperada.path("versionActualId").asLong()).isEqualTo(version);
        assertThat(recuperada.path("contenidoIdentico").asBoolean()).isTrue();
        assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).hasSize(1);
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(propietario)))
            .andExpect(status().isOk()).andExpect(content().string("contenido verificable"));
    }

    @Test void metadatosNoIncrementanVersionYClienteNoPuedeFalsificarla() throws Exception {
        long id = crear(); JsonNode inicial = subir(id, "original");
        mvc.perform(put("/api/documentos/{id}", id).with(actor(propietario)).contentType("application/json").content("{\"descripcion\":\"Descripción modificada\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.versionActualId").value(inicial.path("versionActualId").asLong()));
        mvc.perform(put("/api/documentos/{id}", id).with(actor(propietario)).contentType("application/json").content("{\"version\":\"99.0\"}"))
            .andExpect(status().isBadRequest());
        assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).hasSize(1);
    }

    @Test void rollbackTrasAlmacenarNoPierdeOriginalNiDejaUnaVersionNueva() throws Exception {
        long id = crear(); JsonNode inicial = subir(id, "original");
        doThrow(new RuntimeException("TEST_ONLY_DATABASE_FAILURE")).when(audit).save(any());
        mvc.perform(multipart("/api/documentos/{id}/archivo", id).file(archivo("fallo.txt", "nuevo")).with(actor(propietario)))
            .andExpect(status().isInternalServerError());
        reset(audit);
        Documento actual = documentos.findById(id).orElseThrow();
        assertThat(actual.getVersionActualId()).isEqualTo(inicial.path("versionActualId").asLong());
        assertThat(Files.readString(Path.of(actual.getArchivoRuta()))).isEqualTo("original");
        assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).hasSize(1); assertThat(numeroArchivos()).isEqualTo(1);
    }

    @Test void primerArchivoFallidoNoDejaVersionNiArchivo() throws Exception {
        long id = crear(); doThrow(new RuntimeException("TEST_ONLY_DATABASE_FAILURE")).when(audit).save(any());
        mvc.perform(multipart("/api/documentos/{id}/archivo", id).file(archivo("fallo.txt", "nuevo")).with(actor(propietario)))
            .andExpect(status().isInternalServerError());
        reset(audit);
        assertThat(documentos.findById(id).orElseThrow().getArchivoRuta()).isNull();
        assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).isEmpty(); assertThat(numeroArchivos()).isZero();
    }

    @Test void reemplazosConcurrentesSeSerializanSinPerderArchivos() throws Exception { probarConcurrentes(false); }
    @Test void cargasConcurrentesIdenticasCreanSoloUnaVersion() throws Exception { probarConcurrentes(true); }

    @Test void noSeSolicitaAprobacionSinArchivoONoRevisandoLaVersionActual() throws Exception {
        long id = crear();
        mvc.perform(patch("/api/documentos/{id}/solicitar-aprobacion", id).with(actor(propietario)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("aprobadorId", revisor.getId(), "versionId", 1))))
            .andExpect(status().isConflict());
        subir(id, "primero");
        mvc.perform(patch("/api/documentos/{id}/solicitar-aprobacion", id).with(actor(propietario)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("aprobadorId", revisor.getId(), "versionId", Long.MAX_VALUE))))
            .andExpect(status().isConflict());
        assertThat(solicitudes.findByDocumentoIdOrderByFechaSolicitudDescIdDesc(id)).isEmpty();
    }

    @Test void aprobacionQuedaVinculadaAlArchivoRevisadoYSeConservaTrasReemplazo() throws Exception {
        long id = crear(); long version1 = subir(id, "para revisar").path("versionActualId").asLong();
        long solicitud = solicitar(id, version1);
        resolver(id, solicitud, version1, "aprobar");
        JsonNode actual = subir(id, "nuevo contenido");
        assertThat(actual.path("estado").asText()).isEqualTo("borrador");
        JsonNode historial = historial(id);
        assertThat(historial.path("versiones").get(0).path("aprobaciones").size()).isZero();
        JsonNode decision = historial.path("versiones").get(1).path("aprobaciones").get(0);
        assertThat(decision.path("estado").asText()).isEqualTo("APROBADA");
        assertThat(decision.path("versionId").asLong()).isEqualTo(version1);
        assertThat(decision.path("respondidoPor").asText()).contains("Revisor");
        assertThat(decision.path("fechaRespuesta").asText()).isNotBlank();
    }

    @Test void contenidoIdenticoConservaSuAprobacion() throws Exception {
        long id = crear(); long version = subir(id, "contenido aprobado").path("versionActualId").asLong();
        resolver(id, solicitar(id, version), version, "aprobar");
        JsonNode respuesta = subir(id, "contenido aprobado");
        assertThat(respuesta.path("estado").asText()).isEqualTo("aprobado");
        assertThat(respuesta.path("version").asText()).isEqualTo("1.0");
    }

    @Test void unaRespuestaAntiguaNoPuedeAprobarUnaNuevaSolicitud() throws Exception {
        long id = crear(); long v1 = subir(id, "primero").path("versionActualId").asLong(); long s1 = solicitar(id, v1);
        long v2 = subir(id, "segundo").path("versionActualId").asLong(); long s2 = solicitar(id, v2);
        mvc.perform(patch("/api/documentos/{id}/resolver-aprobacion", id).with(actor(revisor)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("decision", "aprobar", "solicitudId", s1, "versionId", v1))))
            .andExpect(status().isConflict());
        assertThat(solicitudes.findById(s1).orElseThrow().getEstado()).isEqualTo("SUSTITUIDA");
        assertThat(solicitudes.findById(s2).orElseThrow().getEstado()).isEqualTo("PENDIENTE");
        resolver(id, s2, v2, "rechazar");
        assertThat(documentos.findById(id).orElseThrow().getEstado()).isEqualTo("rechazado");
    }

    @Test void rechazaResolverSinIdentificarSolicitudYVersion() throws Exception {
        long id = crear(); solicitar(id, subir(id, "para revisar").path("versionActualId").asLong());
        mvc.perform(patch("/api/documentos/{id}/resolver-aprobacion", id).with(actor(revisor)).contentType("application/json").content("{\"decision\":\"aprobar\"}"))
            .andExpect(status().isConflict());
        mvc.perform(patch("/api/documentos/{id}/resolver-aprobacion", id).with(actor(ajeno)).contentType("application/json").content("{\"decision\":\"aprobar\"}"))
            .andExpect(status().isForbidden());
    }

    @Test void aprobarMientrasSeReemplazaNuncaApruebaElNuevoContenido() throws Exception {
        long id = crear(), v1 = subir(id, "primero").path("versionActualId").asLong(), s1 = solicitar(id, v1);
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch inicio = new CountDownLatch(1);
        try {
            Future<JsonNode> carga = pool.submit(() -> { assertThat(inicio.await(15, TimeUnit.SECONDS)).isTrue(); return subir(id, "nuevo contenido"); });
            Future<Integer> decision = pool.submit(() -> {
                assertThat(inicio.await(15, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(patch("/api/documentos/{id}/resolver-aprobacion", id).with(actor(revisor)).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("decision", "aprobar", "solicitudId", s1, "versionId", v1))))
                    .andReturn().getResponse().getStatus();
            });
            inicio.countDown(); carga.get(30, TimeUnit.SECONDS);
            assertThat(decision.get(30, TimeUnit.SECONDS)).isIn(200, 403, 409);
            Documento actual = documentos.findById(id).orElseThrow();
            assertThat(actual.getVersion()).isEqualTo("2.0"); assertThat(actual.getEstado()).isEqualTo("borrador");
            assertThat(solicitudes.findById(s1).orElseThrow().getEstado()).isIn("APROBADA", "SUSTITUIDA");
        } finally { inicio.countDown(); pool.shutdownNow(); }
    }

    @Test void unArchivoLegadoEsUnaBaseVerificadaSinInventarAprobaciones() throws Exception {
        long id = legado(true); Documento doc = documentos.findById(id).orElseThrow(); doc.setEstado("aprobado"); documentos.saveAndFlush(doc);
        DocumentoSolicitudAprobacion previa = new DocumentoSolicitudAprobacion(); previa.setDocumento(doc); previa.setSolicitante(propietario);
        previa.setAprobador(revisor); previa.setEstado("APROBADA"); previa.setRespondidoPor(revisor); previa.setFechaRespuesta(LocalDateTime.of(2098, 1, 1, 12, 0)); solicitudes.saveAndFlush(previa);
        JsonNode historial = historial(id), base = historial.path("versiones").get(0);
        assertThat(base.path("lineaBase").asBoolean()).isTrue(); assertThat(base.path("version").asText()).isEqualTo("2.3");
        assertThat(base.path("aprobaciones").size()).isZero(); assertThat(historial.path("aprobacionesSinVersion").size()).isEqualTo(1);
        assertThat(historial.path("documento").path("estado").asText()).isEqualTo("aprobado");
        assertThat(subir(id, "reemplazo").path("version").asText()).isEqualTo("3.0");
        assertThat(archivos.resolve("legado.txt")).exists();
    }

    @Test void noInventaVersionesDeUnArchivoLegadoInexistente() throws Exception {
        long id = legado(false); JsonNode historial = historial(id);
        assertThat(historial.path("versiones").size()).isZero(); assertThat(historial.path("archivoActualDisponible").asBoolean()).isFalse();
        assertThat(documentos.findById(id).orElseThrow().getVersion()).isEqualTo("2.3");
        assertThat(subir(id, "archivo recuperado").path("version").asText()).isEqualTo("1.0");
        JsonNode posterior = historial(id);
        assertThat(posterior.path("registrosAnteriores").get(0).path("version").asText()).isEqualTo("2.3");
        assertThat(posterior.path("registrosAnteriores").get(0).path("disponible").asBoolean()).isFalse();
    }

    @Test void registrosDeVersionSinArchivoSeConservanComoAntecedentes() throws Exception {
        long id = crear(); DocumentoVersion previa = new DocumentoVersion(); previa.setDocumentoId(id); previa.setVersion("1.0");
        previa.setAutor("Registro ficticio anterior"); previa.setFecha(LocalDate.of(2098, 1, 1)); versiones.saveAndFlush(previa);
        subir(id, "primero verificable"); JsonNode historial = historial(id);
        assertThat(historial.path("versiones").size()).isEqualTo(1); assertThat(historial.path("registrosAnteriores").size()).isEqualTo(1);
        assertThat(historial.path("registrosAnteriores").get(0).path("disponible").asBoolean()).isFalse();
    }

    @Test void versionesRequierenUsuarioPermisoFuncionalYAccesoAlDocumento() throws Exception {
        long id = crear(), version = subir(id, "privado").path("versionActualId").asLong();
        mvc.perform(get("/api/documentos/{id}/versiones", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(user(propietario.getCorreo()).authorities(new SimpleGrantedAuthority("Documentos:Ver"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/documentos/{id}/versiones", id).with(actor(ajeno))).andExpect(status().isNotFound());
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(ajeno))).andExpect(status().isNotFound());
        long otro = crear();
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", otro, version).with(actor(propietario))).andExpect(status().isNotFound());
    }

    @Test void accesoExplicitoPermiteDescargarHistoricoPeroUnaCarpetaCompartidaNo() throws Exception {
        long id = crear(), version = subir(id, "privado").path("versionActualId").asLong();
        Carpeta carpeta = new Carpeta(); carpeta.setNombre("Carpeta ficticia"); carpeta.setTipo("COMPARTIDA"); carpeta.setPropietario(propietario);
        carpeta = carpetas.saveAndFlush(carpeta); folderIds.add(carpeta.getId());
        CarpetaUsuario miembro = new CarpetaUsuario(); miembro.setCarpeta(carpeta); miembro.setUsuario(revisor); miembros.saveAndFlush(miembro);
        Documento doc = documentos.findById(id).orElseThrow(); doc.setCarpetaId(carpeta.getId()); documentos.saveAndFlush(doc);
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(revisor))).andExpect(status().isNotFound());
        mvc.perform(post("/api/documentos/{id}/compartidos", id).with(actor(propietario)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("usuarioId", revisor.getId(), "permiso", "LECTURA")))).andExpect(status().isOk());
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(revisor))).andExpect(status().isOk()).andExpect(content().string("privado"));
    }

    @Test void porUnidadProtegeTambienLasDescargasHistoricasYNoPermiteCompartirFuera() throws Exception {
        long id = crear(), version = subir(id, "contenido de unidad").path("versionActualId").asLong();
        Unidad unidad = new Unidad(); unidad.setNombre("Unidad ficticia " + UUID.randomUUID()); unidad = unidades.saveAndFlush(unidad); unidadIds.add(unidad.getId());
        UsuarioUnidad miembro = new UsuarioUnidad(); miembro.setUsuario(revisor); miembro.setUnidad(unidad);
        miembro.setRol(roles.findAll().stream().filter(r -> "Miembro".equals(r.getNombre())).findFirst().orElseThrow()); membresias.saveAndFlush(miembro);
        Documento doc = documentos.findById(id).orElseThrow(); doc.setClasificacion(DocumentoClasificacion.RESTRINGIDO); doc.setArea(unidad.getNombre()); documentos.saveAndFlush(doc);
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(revisor))).andExpect(status().isOk());
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(ajeno))).andExpect(status().isNotFound());
        mvc.perform(post("/api/documentos/{id}/compartidos", id).with(actor(propietario)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("usuarioId", ajeno.getId(), "permiso", "LECTURA")))).andExpect(status().isBadRequest());
    }

    @Test void institucionalYPrivilegiosAdministrativosMantienenSuAlcance() throws Exception {
        long id = crear(), version = subir(id, "contenido institucional").path("versionActualId").asLong();
        Documento doc = documentos.findById(id).orElseThrow(); doc.setClasificacion(DocumentoClasificacion.PUBLICO); doc = documentos.saveAndFlush(doc);
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(ajeno))).andExpect(status().isOk());
        doc.setClasificacion(DocumentoClasificacion.CONFIDENCIAL); documentos.saveAndFlush(doc);
        Usuario superadmin = usuario("Superadmin", "Superadmin");
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(superadmin))).andExpect(status().isOk());
    }

    @Test void unAccesoExpiradoNoDescargaVersionesAunqueConozcaLaUrl() throws Exception {
        long id = crear(), version = subir(id, "privado").path("versionActualId").asLong();
        mvc.perform(post("/api/documentos/{id}/compartidos", id).with(actor(propietario)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("usuarioId", revisor.getId(), "permiso", "LECTURA")))).andExpect(status().isOk());
        DocumentoUsuarioAcceso acceso = accesos.findByDocumentoIdAndUsuarioId(id, revisor.getId()).orElseThrow();
        acceso.setExpiraEn(LocalDateTime.now().minusMinutes(1)); accesos.saveAndFlush(acceso);
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(revisor))).andExpect(status().isNotFound());
    }

    @Test void solicitudLegadaSinVersionSeConservaComoSustituidaSinAprobarElArchivoActual() throws Exception {
        long id = legado(true); Documento doc = documentos.findById(id).orElseThrow();
        DocumentoSolicitudAprobacion vieja = new DocumentoSolicitudAprobacion(); vieja.setDocumento(doc); vieja.setSolicitante(propietario); vieja.setAprobador(revisor);
        vieja = solicitudes.saveAndFlush(vieja);
        long version = historial(id).path("documento").path("versionActualId").asLong();
        long nueva = solicitar(id, version);
        DocumentoSolicitudAprobacion anterior = solicitudes.findById(vieja.getId()).orElseThrow();
        assertThat(anterior.getEstado()).isEqualTo("SUSTITUIDA"); assertThat(anterior.getVersion()).isNull();
        assertThat(nueva).isNotEqualTo(vieja.getId()); assertThat(documentos.findById(id).orElseThrow().getEstado()).isEqualTo("en_revision");
    }

    @Test void aliasesSeConservanSinAparentarPoliticasAdicionales() throws Exception {
        long id = crear(); Documento doc = documentos.findById(id).orElseThrow(); doc.setClasificacion(DocumentoClasificacion.DATOS_SENSIBLES); documentos.saveAndFlush(doc);
        mvc.perform(put("/api/documentos/{id}", id).with(actor(propietario)).contentType("application/json").content("{\"clasificacion\":\"CONFIDENCIAL\",\"descripcion\":\"Actualizada\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.politicaAcceso").value("CONFIDENCIAL"));
        assertThat(documentos.findById(id).orElseThrow().getClasificacion()).isEqualTo(DocumentoClasificacion.DATOS_SENSIBLES);
    }

    @Test void personalNoPuedeUsarseComoUnidadInstitucionalRestringida() throws Exception {
        Map<String, Object> body = datosCreacion(); body.put("clasificacion", "RESTRINGIDO");
        mvc.perform(post("/api/documentos").with(actor(propietario)).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test void eliminarRetiraElDocumentoSinDestruirArchivosNiDecisiones() throws Exception {
        long id = crear(), version = subir(id, "conservar").path("versionActualId").asLong();
        resolver(id, solicitar(id, version), version, "aprobar");
        mvc.perform(delete("/api/documentos/{id}", id).with(actor(propietario))).andExpect(status().isNoContent());
        assertThat(documentos.findById(id)).isEmpty(); assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).hasSize(1);
        assertThat(solicitudes.findById(jdbc.queryForObject("select id from documento_solicitudes_aprobacion where documento_id = ?", Long.class, id))).isPresent();
        assertThat(numeroArchivos()).isEqualTo(1);
        mvc.perform(get("/api/documentos/{id}/versiones/{v}/descargar", id, version).with(actor(propietario))).andExpect(status().isNotFound());
    }

    private void probarConcurrentes(boolean identicas) throws Exception {
        long id = crear(); subir(id, "inicial"); ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch listos = new CountDownLatch(2), inicio = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) { final String texto = identicas ? "igual concurrente" : "cambio " + i;
                resultados.add(pool.submit(() -> { listos.countDown(); assertThat(inicio.await(15, TimeUnit.SECONDS)).isTrue(); return subir(id, texto); }));
            }
            assertThat(listos.await(15, TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            for (Future<JsonNode> resultado : resultados) resultado.get(30, TimeUnit.SECONDS);
            assertThat(versiones.findByDocumentoIdOrderByIdDesc(id)).hasSize(identicas ? 2 : 3);
            assertThat(numeroArchivos()).isEqualTo(identicas ? 2 : 3);
        } finally { inicio.countDown(); pool.shutdownNow(); }
    }

    private long crear() throws Exception {
        JsonNode respuesta = json.readTree(mvc.perform(post("/api/documentos").with(actor(propietario)).contentType("application/json").content(json.writeValueAsString(datosCreacion())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long id = respuesta.path("id").asLong(); docIds.add(id); return id;
    }
    private Map<String, Object> datosCreacion() {
        return new LinkedHashMap<>(Map.of("nombre", "Documento ficticio", "categoria", "Informe", "area", "Personal", "clasificacion", "CONFIDENCIAL", "creacionId", UUID.randomUUID().toString()));
    }
    private JsonNode subir(long id, String texto) throws Exception {
        return json.readTree(mvc.perform(multipart("/api/documentos/{id}/archivo", id).file(archivo("prueba.txt", texto)).with(actor(propietario)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode historial(long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/documentos/{id}/versiones", id).with(actor(propietario)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private long solicitar(long id, long version) throws Exception {
        JsonNode respuesta = json.readTree(mvc.perform(patch("/api/documentos/{id}/solicitar-aprobacion", id).with(actor(propietario)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("aprobadorId", revisor.getId(), "versionId", version))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return respuesta.path("solicitudAprobacion").path("id").asLong();
    }
    private void resolver(long id, long solicitud, long version, String decision) throws Exception {
        mvc.perform(patch("/api/documentos/{id}/resolver-aprobacion", id).with(actor(revisor)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("decision", decision, "solicitudId", solicitud, "versionId", version))))
            .andExpect(status().isOk());
    }
    private long legado(boolean existe) throws Exception {
        Documento doc = new Documento(); doc.setNombre("Documento legado ficticio"); doc.setCategoria("Informe"); doc.setArea("Personal"); doc.setVersion("2.3");
        doc.setFecha(LocalDate.of(2098, 1, 1)); doc.setPropietarioUsuarioId(propietario.getId()); doc.setPropietario("Propietario de prueba");
        if (existe) Files.writeString(archivos.resolve("legado.txt"), "contenido legado");
        doc.setArchivoRuta(archivos.resolve("legado.txt").toString()); doc = documentos.saveAndFlush(doc); docIds.add(doc.getId()); return doc.getId();
    }
    private long numeroArchivos() throws Exception { try (var stream = Files.list(archivos)) { return stream.filter(Files::isRegularFile).count(); } }
    private MockMultipartFile archivo(String nombre, String contenido) { return new MockMultipartFile("file", nombre, "text/plain", contenido.getBytes(StandardCharsets.UTF_8)); }
    private RequestPostProcessor actor(Usuario usuario) {
        return user(usuario.getCorreo()).authorities(Arrays.stream(new String[]{"Ver", "Crear", "Editar", "Descargar", "Eliminar", "Crear carpeta"})
            .map(permiso -> new SimpleGrantedAuthority("Documentos:" + permiso)).toArray(SimpleGrantedAuthority[]::new));
    }
    private Usuario usuario(String nombre, String rol) {
        Usuario usuario = new Usuario(); usuario.setNombre(nombre); usuario.setApellido("Prueba"); usuario.setUsername("versiones-" + UUID.randomUUID());
        usuario.setCorreo("versiones-" + UUID.randomUUID() + "@example.test"); usuario.setPassword("TEST_ONLY_NOT_REAL");
        usuario.setRoles(Set.of(roles.findAll().stream().filter(r -> rol.equals(r.getNombre())).findFirst().orElseThrow()));
        usuario = usuarios.saveAndFlush(usuario); userIds.add(usuario.getId()); return usuario;
    }
}
