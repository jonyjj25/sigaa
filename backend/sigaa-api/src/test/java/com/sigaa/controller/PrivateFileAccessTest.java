package com.sigaa.controller;

import com.sigaa.config.GlobalExceptionHandler;
import com.sigaa.config.SecurityConfig;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.security.JwtFilter;
import com.sigaa.security.JwtUtil;
import com.sigaa.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cadena HTTP y autorización reales; repositorios simulados, sin conexión a BD ni configuración local. */
@SpringJUnitWebConfig(PrivateFileAccessTest.Config.class)
@TestPropertySource(properties = "app.preview.libreoffice-command=")
class PrivateFileAccessTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, GlobalExceptionHandler.class, DocumentoController.class,
            DocumentoAccessService.class, ActaController.class, ActaAccessService.class,
            AsistenciaAccessService.class, ReporteController.class})
    static class Config {
        @Bean SecurityPolicyService policy() { return mock(SecurityPolicyService.class); }
        @Bean UserDetailsService userDetails() { return mock(UserDetailsService.class); }
        @Bean JwtFilter jwtFilter(UsuarioRepository usuarios, UserDetailsService userDetails) {
            return new JwtFilter(mock(JwtUtil.class), userDetails, mock(SecurityIpService.class), usuarios);
        }
        @Bean DocumentoRepository documentos() { return mock(DocumentoRepository.class); }
        @Bean CarpetaRepository carpetas() { return mock(CarpetaRepository.class); }
        @Bean CarpetaUsuarioRepository carpetaUsuarios() { return mock(CarpetaUsuarioRepository.class); }
        @Bean DocumentoVersionRepository versiones() { return mock(DocumentoVersionRepository.class); }
        @Bean TipoDocumentoRepository tiposDocumento() { return mock(TipoDocumentoRepository.class); }
        @Bean AuditLogRepository auditoria() { return mock(AuditLogRepository.class); }
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean OrgUnitRepository unidades() { return mock(OrgUnitRepository.class); }
        @Bean UsuarioUnidadRepository membresias() { return mock(UsuarioUnidadRepository.class); }
        @Bean DocumentoSolicitudAprobacionRepository aprobaciones() { return mock(DocumentoSolicitudAprobacionRepository.class); }
        @Bean DocumentoUsuarioAccesoRepository accesos() { return mock(DocumentoUsuarioAccesoRepository.class); }
        @Bean InternalNotificationService notificaciones() { return mock(InternalNotificationService.class); }
        @Bean ActaRepository actas() { return mock(ActaRepository.class); }
        @Bean ActaParticipanteRepository participantes() { return mock(ActaParticipanteRepository.class); }
        @Bean ActaAcuerdoRepository acuerdos() { return mock(ActaAcuerdoRepository.class); }
        @Bean ActaAdjuntoRepository adjuntos() { return mock(ActaAdjuntoRepository.class); }
        @Bean ActaFirmaRepository firmas() { return mock(ActaFirmaRepository.class); }
        @Bean AsistenciaSesionRepository sesiones() { return mock(AsistenciaSesionRepository.class); }
        @Bean AsistenciaRegistroRepository registros() { return mock(AsistenciaRegistroRepository.class); }
        @Bean AsistenciaInvitadoRepository invitados() { return mock(AsistenciaInvitadoRepository.class); }
        @Bean TareaRepository tareas() { return mock(TareaRepository.class); }
        @Bean TareaAccessService accesoTareas() { return mock(TareaAccessService.class); }
        @Bean TareaAdjuntoRepository adjuntosTareas() { return mock(TareaAdjuntoRepository.class); }
        @Bean ReporteRepository reportes() { return mock(ReporteRepository.class); }
        @Bean ReporteProgramacionRepository programaciones() { return mock(ReporteProgramacionRepository.class); }
        @Bean ReporteGenerationService generacion() { return mock(ReporteGenerationService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired DocumentoRepository documentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired ActaRepository actas;
    @Autowired ActaAdjuntoRepository adjuntos;
    @Autowired ReporteRepository reportes;
    private MockMvc mvc;
    private final List<Path> fixtures = new ArrayList<>();
    private final Map<String, Path> archivos = new HashMap<>();
    private String nombre;
    private Documento documento;
    private Acta acta;
    private Reporte reporte;

    @BeforeEach void preparar() throws Exception {
        reset(new Object[] {documentos, usuarios, actas, adjuntos, reportes});
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        nombre = UUID.randomUUID() + "_informe_privado.txt";
        for (String carpeta : List.of("documentos", "actas", "reportes", "fotografias")) {
            Path directorio = Path.of("uploads", carpeta).toAbsolutePath().normalize();
            Files.createDirectories(directorio);
            Path archivo = Files.writeString(directorio.resolve(nombre), "contenido sintetico " + carpeta);
            fixtures.add(archivo); archivos.put(carpeta, archivo);
        }
        Usuario propietario = usuario(1L, "propietario@example.test");
        Usuario otro = usuario(2L, "otro@example.test");
        when(usuarios.findByCorreo(propietario.getCorreo())).thenReturn(Optional.of(propietario));
        when(usuarios.findByCorreo(otro.getCorreo())).thenReturn(Optional.of(otro));
        when(usuarios.findByCorreoIgnoreCase(propietario.getCorreo())).thenReturn(Optional.of(propietario));
        when(usuarios.findByCorreoIgnoreCase(otro.getCorreo())).thenReturn(Optional.of(otro));
        documento = new Documento(); documento.setId(10L); documento.setNombre("informe_privado.txt");
        documento.setPropietarioUsuarioId(1L); documento.setClasificacion(DocumentoClasificacion.CONFIDENCIAL);
        documento.setFecha(LocalDate.of(2026, 10, 8)); documento.setArchivoRuta(archivos.get("documentos").toString());
        when(documentos.findById(10L)).thenReturn(Optional.of(documento));
        when(documentos.findAllByOrderByFechaDesc()).thenReturn(List.of(documento));
        acta = new Acta(); acta.setId(20L); acta.setFecha(LocalDate.of(2026, 10, 8)); acta.setHora(LocalTime.NOON);
        when(actas.findById(20L)).thenReturn(Optional.of(acta));
        when(actas.findByParticipanteUsuario(eq(propietario.getCorreo()), anyString())).thenReturn(List.of(acta));
        ActaAdjunto adjunto = new ActaAdjunto(); adjunto.setId(21L); adjunto.setActaId(20L);
        adjunto.setNombreOriginal("informe_privado.txt"); adjunto.setRuta(archivos.get("actas").toString()); adjunto.setTipoMime("text/plain");
        when(adjuntos.findById(21L)).thenReturn(Optional.of(adjunto));
        reporte = new Reporte(); reporte.setId(30L); reporte.setNombre("Reporte sintético");
        reporte.setFecha(LocalDate.of(2026, 10, 8)); reporte.setArchivosRuta(archivos.get("reportes").toString());
        when(reportes.findById(30L)).thenReturn(Optional.of(reporte));
    }

    @AfterEach void limpiarFixtures() throws Exception {
        for (Path fixture : fixtures) Files.deleteIfExists(fixture);
        fixtures.clear(); archivos.clear();
    }

    @ParameterizedTest @ValueSource(strings = {"documentos", "actas", "reportes"})
    void rutaFisicaNoEntregaArchivoAunqueElUsuarioTengaTodosLosPermisos(String carpeta) throws Exception {
        assertThat(archivos.get(carpeta)).exists();
        String ruta = "/uploads/" + carpeta + "/" + nombre;
        var autorizado = user("propietario@example.test").authorities(autoridades(
                "ROLE_administrador", "Documentos:Descargar", "Actas:Descargar", "Reporte:Ver"));
        mvc.perform(get(ruta).with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(head(ruta).with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    }

    @Test void soloElDirectorioPublicoDeFotografiasSePublica() throws Exception {
        mvc.perform(get("/uploads/fotografias/" + nombre)).andExpect(status().isOk())
                .andExpect(content().string("contenido sintetico fotografias"));
        mvc.perform(head("/uploads/fotografias/" + nombre)).andExpect(status().isOk());
        Path fuera = Files.writeString(Path.of("uploads", nombre), "fixture fuera del directorio público");
        fixtures.add(fuera);
        mvc.perform(get("/uploads/" + nombre).with(user("propietario@example.test")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {
            "/api/documentos/10/descargar", "/api/documentos/10/vista-previa",
            "/api/actas/20/adjuntos/21/descargar", "/api/reportes/30/descargar-archivo/0"})
    void endpointsPrivadosRechazanAnonimosYUsuariosSinPermiso(String endpoint) throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint).with(user("propietario@example.test"))).andExpect(status().isForbidden());
    }

    @Test void catalogoDeCategoriasEstaPublicadoParaUsuariosConPermiso() throws Exception {
        mvc.perform(get("/api/documentos/categorias").with(user("propietario@example.test")
                .authorities(autoridades("Documentos:Ver"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItems("Acta", "Informe")));
    }

    @Test void catalogoDeCategoriasConservaAutenticacionYPermisos() throws Exception {
        mvc.perform(get("/api/documentos/categorias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documentos/categorias").with(user("otro@example.test")
                .authorities(autoridades("Documentos:Descargar"))))
            .andExpect(status().isForbidden());
    }

    @Test void documentoExigePermisoYAccesoAlRecurso() throws Exception {
        mvc.perform(get("/api/documentos/10/descargar").with(user("propietario@example.test")
                .authorities(autoridades("Documentos:Descargar"))))
                .andExpect(status().isOk()).andExpect(content().string("contenido sintetico documentos"));
        mvc.perform(get("/api/documentos/10/descargar").with(user("otro@example.test")
                .authorities(autoridades("Documentos:Descargar")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/documentos/10/vista-previa").with(user("propietario@example.test")
                .authorities(autoridades("Documentos:Ver"))))
                .andExpect(status().isOk()).andExpect(content().string("contenido sintetico documentos"));
        mvc.perform(get("/api/documentos/10/vista-previa").with(user("otro@example.test")
                .authorities(autoridades("Documentos:Ver")))).andExpect(status().isNotFound());
    }

    @Test void actaExigePermisoParticipacionYCorrespondenciaDelAdjunto() throws Exception {
        var participante = user("propietario@example.test").authorities(autoridades("ROLE_miembro", "Actas:Descargar"));
        mvc.perform(get("/api/actas/20/adjuntos/21/descargar").with(participante))
                .andExpect(status().isOk()).andExpect(content().string("contenido sintetico actas"));
        mvc.perform(get("/api/actas/20/adjuntos/21/descargar").with(user("otro@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Descargar")))).andExpect(status().isForbidden());
        adjuntos.findById(21L).orElseThrow().setActaId(99L);
        mvc.perform(get("/api/actas/20/adjuntos/21/descargar").with(participante)).andExpect(status().isNotFound());
    }

    @Test void reporteExigeElMismoPermisoQueSuConsulta() throws Exception {
        mvc.perform(get("/api/reportes/30/descargar-archivo/0").with(user("propietario@example.test")
                .authorities(autoridades("Reporte:Ver"))))
                .andExpect(status().isOk()).andExpect(content().string("contenido sintetico reportes"));
        mvc.perform(get("/api/reportes/30/descargar-archivo/0").with(user("propietario@example.test")
                .authorities(autoridades("Reporte:Crear")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/30/descargar-archivo/9").with(user("propietario@example.test")
                .authorities(autoridades("Reporte:Ver")))).andExpect(status().isNotFound());
    }

    @Test void respuestasDeConsultaNoExponenRutasFisicas() throws Exception {
        mvc.perform(get("/api/documentos").with(user("propietario@example.test")
                .authorities(autoridades("Documentos:Ver"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].archivoRuta").value("/api/documentos/10/descargar"));
        mvc.perform(get("/api/reportes/30").with(user("propietario@example.test")
                .authorities(autoridades("Reporte:Ver"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosRuta").value("/api/reportes/30/descargar-archivo/0"))
                .andExpect(jsonPath("$.archivosNombres[0]").value("informe_privado.txt"));
        assertThat(documento.getArchivoRuta()).isEqualTo(archivos.get("documentos").toString());
        assertThat(reporte.getArchivosRuta()).isEqualTo(archivos.get("reportes").toString());
    }

    @Test void metadataDeReportesNoExponeDirectoriosAunqueLaRutaHistoricaUseOtroSeparador() throws Exception {
        reporte.setArchivosRuta("C:\\directorio-sintetico\\uploads\\reportes\\12345678-1234-1234-1234-123456789abc_informe_final.txt");
        mvc.perform(get("/api/reportes/30").with(user("propietario@example.test")
                .authorities(autoridades("Reporte:Ver"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosRuta").value("/api/reportes/30/descargar-archivo/0"))
                .andExpect(jsonPath("$.archivosNombres[0]").value("informe_final.txt"));
    }

    @Test void subirAdjuntoDeActaDevuelveSoloLaReferenciaAutorizada() throws Exception {
        when(adjuntos.save(any(ActaAdjunto.class))).thenAnswer(invocacion -> {
            ActaAdjunto adjunto = invocacion.getArgument(0); adjunto.setId(42L);
            fixtures.add(Path.of(adjunto.getRuta()));
            return adjunto;
        });
        var archivo = new org.springframework.mock.web.MockMultipartFile(
                "file", "informe_privado.txt", "text/plain", "fixture sintetico".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/actas/20/adjuntos").file(archivo).with(user("propietario@example.test")
                .authorities(autoridades("ROLE_miembro", "Actas:Crear"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruta").value("/api/actas/20/adjuntos/42/descargar"));
    }

    @Test void unFalloDeArchivoEnReporteNoExponeLaRutaInternaEnSuRespuesta() throws Exception {
        when(reportes.findByIdForUpdate(30L)).thenReturn(Optional.of(reporte));
        var archivo = new org.springframework.mock.web.MockMultipartFile("files", "archivo.txt", "text/plain", new byte[]{1}) {
            @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                throw new java.io.IOException("No se puede leer C:\\directorio-sintetico\\archivo.txt");
            }
        };
        mvc.perform(multipart("/api/reportes/30/archivos").file(archivo).with(user("propietario@example.test")
                .authorities(autoridades("Reporte:Subir archivos"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosRuta").value("/api/reportes/30/descargar-archivo/0"))
                .andExpect(jsonPath("$.errores[0]").value("archivo.txt: No se pudo validar o almacenar el archivo."));
    }

    private Usuario usuario(Long id, String correo) {
        Usuario usuario = new Usuario(); usuario.setId(id); usuario.setCorreo(correo); usuario.setNombre("Usuario sintético");
        return usuario;
    }

    private SimpleGrantedAuthority[] autoridades(String... valores) {
        return Arrays.stream(valores).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
    }
}
