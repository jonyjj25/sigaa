package com.sigaa.service;

import com.sigaa.controller.NotaController;
import com.sigaa.dto.NotaCreateRequest;
import com.sigaa.dto.NotaUpdateRequest;
import com.sigaa.dto.NotaSeguimientoRequest;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.reports.scheduler-delay-ms=3600000")
@ActiveProfiles("test")
class NotaSeguimientoIntegrationTest {
    @Autowired NotaService servicio;
    @Autowired NotaController controller;
    @Autowired NotaTrazabilidadService trazabilidad;
    @Autowired NotaDocumentoService documentos;
    @MockitoBean NotaPdfStorageService archivos;
    @Autowired NotaNumeroRepository numeros;
    @Autowired NotaHistorialRepository historial;
    @Autowired OrganizationRepository instituciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean UsuarioRepository usuarios;
    private final List<Long> usuarioIds = new ArrayList<>();
    private Usuario creador, responsable, nuevoResponsable, directora;
    private NotaNumero numero;
    private Nota nota;
    private Organization institucion;
    private LocalDateTime fechaReserva;

    @BeforeEach void datosPropios() {
        creador = usuario("Elaborador"); responsable = usuario("Responsable");
        nuevoResponsable = usuario("Nuevo responsable"); directora = usuario("Directora");
        directora.setCargo("Comisionada"); directora.setInstitucion("Institución de prueba"); usuarios.saveAndFlush(directora);
        doReturn(List.of(directora)).when(usuarios).findActiveDirectors();
        institucion = new Organization(); institucion.setNombre("Receptor de prueba " + UUID.randomUUID());
        institucion.setActivo(true); institucion = instituciones.saveAndFlush(institucion);
        int anio = LocalDate.now(ZoneId.of("America/Panama")).getYear();
        int consecutivo = 999;
        while (jdbc.queryForObject("select count(*) from nota_numeros where numero = ? or (anio = ? and consecutivo = ?)",
            Integer.class, NotaNumeroService.formatear(consecutivo, anio), anio, consecutivo) != 0) consecutivo--;
        assertThat(consecutivo).isPositive();
        numero = new NotaNumero(); numero.setAnio(anio); numero.setConsecutivo(consecutivo);
        numero.setNumero(NotaNumeroService.formatear(consecutivo, anio)); numero.setEstado("RESERVADO");
        numero.setReservadoPor(creador); fechaReserva = LocalDateTime.now().minusMinutes(2);
        numero.setReservadoDesde(fechaReserva); numero.setReservadoHasta(LocalDateTime.now().plusMinutes(8));
        numero = numeros.saveAndFlush(numero); autenticar(creador);
    }

    @AfterEach void limpiarDatosPropios() {
        SecurityContextHolder.clearContext();
        if (nota != null) {
            jdbc.update("delete from audit_logs where entidad = 'nota' and entidad_id = ?", nota.getId());
            jdbc.update("delete from notas where id = ?", nota.getId());
        }
        if (numero != null) jdbc.update("delete from nota_numeros where id = ?", numero.getId());
        if (institucion != null) jdbc.update("delete from organizations where id = ?", institucion.getId());
        for (Long id : usuarioIds) {
            jdbc.update("delete from notificaciones where usuario_id = ?", id);
            jdbc.update("delete from usuarios where id = ?", id);
        }
    }

    @Test void cicloCompletoConservaTodosLosEventosUsuariosFechasYFirmasDiferentes() {
        crear();
        NotaUpdateRequest cambios = actualizacion(); cambios.setSeguimientoUsuarioId(nuevoResponsable.getId());
        cambios.setAsunto("Asunto corregido"); cambios.setDescripcion("Comentario de la edición");
        servicio.actualizar(nota.getId(), cambios, creador);
        firmar();
        avance(null, "Entrega coordinada con el receptor");
        avance("RECIBIDA", "Recibida en la oficina del destinatario");
        avance("SIN_RESPUESTA", "Aún no se ha recibido una respuesta");
        avance("RESPONDIDA", "Respuesta recibida por correspondencia");
        avance("FIRMADA_RECEPTOR", "El receptor firmó el acuse");
        List<NotaHistorial> antes = eventos();
        servicio.anular(nota.getId(), "Documento reemplazado mediante otra nota", creador);
        List<NotaHistorial> despues = eventos();
        assertThat(despues).extracting(NotaHistorial::getAccion).containsExactly(
            "RESERVAR_NUMERO", "OCUPAR_NUMERO", "CREAR_BORRADOR", "SEGUIMIENTO_INICIAL", "ACTUALIZAR",
            "CAMBIAR_RESPONSABLE", "SEGUIMIENTO", "OFICIALIZAR", "FIRMAR", "SEGUIMIENTO", "RECIBIDA",
            "SIN_RESPUESTA", "RESPONDIDA", "FIRMADA_RECEPTOR", "ANULAR");
        assertThat(despues.subList(0, antes.size())).extracting(NotaHistorial::getId)
            .containsExactlyElementsOf(antes.stream().map(NotaHistorial::getId).toList());
        assertThat(despues).allSatisfy(evento -> {
            assertThat(evento.getCreatedAt()).isNotNull(); assertThat(evento.getUsuario().getId()).isEqualTo(creador.getId());
        });
        assertThat(despues).isSortedAccordingTo(Comparator.comparing(NotaHistorial::getCreatedAt).thenComparing(NotaHistorial::getId));
        assertThat(despues.getFirst().getCreatedAt()).isCloseTo(fechaReserva, within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(trazabilidad.seguimientoActual(nota, despues).estado()).isEqualTo(NotaSeguimientoEstado.FIRMADA_RECEPTOR);
        assertThat(jdbc.queryForObject("select estado from notas where id = ?", String.class, nota.getId())).isEqualTo("ANULADA");
        assertThat(auditorias("FIRMAR")).isEqualTo(1); assertThat(auditorias("FIRMADA_RECEPTOR")).isEqualTo(1);
        assertThat(auditorias("CAMBIAR_RESPONSABLE")).isEqualTo(1);
    }

    @Test void comentarNoCambiaElAvanceYNuncaNotificaAlMismoResponsableQueActua() {
        crear(); firmar(); int avisosAntes = avisos();
        autenticar(responsable);
        servicio.seguimiento(nota.getId(), new NotaSeguimientoRequest(null, "Entregaremos la nota mañana"), responsable, true, Set.of());
        assertThat(trazabilidad.seguimientoActual(nota, eventos()).estado()).isEqualTo(NotaSeguimientoEstado.PENDIENTE_ENTREGA);
        assertThat(eventos().getLast().getUsuario().getId()).isEqualTo(responsable.getId());
        assertThat(auditorias("SEGUIMIENTO")).isEqualTo(1); assertThat(avisos()).isEqualTo(avisosAntes);
    }

    @Test void permisosSeparanComentariosEstadosYFirmaDelReceptor() {
        crear(); firmar(); int antes = eventos().size(); int avisosAntes = avisos();
        assertThatThrownBy(() -> servicio.seguimiento(nota.getId(), new NotaSeguimientoRequest(null, "Comentario"), creador, false, Set.of()))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.seguimiento(nota.getId(), new NotaSeguimientoRequest("RECIBIDA", ""), creador, true, Set.of()))
            .isInstanceOf(AccessDeniedException.class);
        Set<NotaSeguimientoEstado> ordinarios = EnumSet.allOf(NotaSeguimientoEstado.class); ordinarios.remove(NotaSeguimientoEstado.FIRMADA_RECEPTOR);
        assertThatThrownBy(() -> servicio.seguimiento(nota.getId(), new NotaSeguimientoRequest("FIRMADA_RECEPTOR", ""), creador, true, ordinarios))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.seguimiento(nota.getId(), new NotaSeguimientoRequest("RECIBIDA", "Comentario"), creador, false, ordinarios))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> avance("ESTADO_FALSO", "" )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> avance(null, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> avance(null, "x".repeat(5001))).isInstanceOf(IllegalArgumentException.class);
        assertThat(eventos()).hasSize(antes); assertThat(avisos()).isEqualTo(avisosAntes);
    }

    @Test void responsableConsultaLaMismaTrazaYRecibeSoloLasAccionesAutorizadas() {
        crear(); firmar();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            responsable.getCorreo(), "", List.of(new SimpleGrantedAuthority("Notas:Ver"),
            new SimpleGrantedAuthority("Notas:Dar seguimiento"), new SimpleGrantedAuthority("Notas:Cambiar seguimiento"))));
        Map<String, Object> detalle = controller.obtener(nota.getId());
        assertThat(detalle.get("historial")).isInstanceOf(List.class);
        assertThat((List<?>) detalle.get("historial")).hasSize(eventos().size());
        assertThat(detalle.get("seguimiento")).isInstanceOf(Map.class);
        Map<?, ?> seguimiento = (Map<?, ?>) detalle.get("seguimiento");
        assertThat(seguimiento.get("estado")).isEqualTo("PENDIENTE_ENTREGA");
        assertThat(seguimiento.get("puedeComentar")).isEqualTo(true);
        List<?> opciones = (List<?>) seguimiento.get("opciones");
        assertThat(opciones).hasSize(4);
        assertThat(opciones.stream().map(opcion -> ((Map<?, ?>) opcion).get("estado").toString()))
            .doesNotContain("FIRMADA_RECEPTOR");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            responsable.getCorreo(), "", List.of(new SimpleGrantedAuthority("Notas:Ver"))));
        seguimiento = (Map<?, ?>) controller.obtener(nota.getId()).get("seguimiento");
        assertThat(seguimiento.get("puedeComentar")).isEqualTo(false);
        assertThat((List<?>) seguimiento.get("opciones")).isEmpty();
    }

    @Test void rollbackRevierteSeguimientoAuditoriaYAvisoSinPerderLaFirmaAnterior() {
        crear(); firmar(); int antes = eventos().size(); int avisosAntes = avisos();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            avance("RECIBIDA", "Recibida en recepción"); throw new IllegalStateException("Fallo posterior");
        })).hasMessageContaining("posterior");
        assertThat(eventos()).hasSize(antes); assertThat(auditorias("RECIBIDA")).isZero();
        assertThat(avisos()).isEqualTo(avisosAntes);
        assertThat(trazabilidad.seguimientoActual(nota, eventos()).estado()).isEqualTo(NotaSeguimientoEstado.PENDIENTE_ENTREGA);
    }

    @Test void dosRegistrosConcurrentesDelMismoAvanceNoDuplicanHistorialAuditoriaNiAviso() throws Exception {
        crear(); firmar(); int avisosAntes = avisos();
        CountDownLatch listos = new CountDownLatch(2), iniciar = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> tarea = () -> {
                autenticar(creador);
                try { listos.countDown(); assertThat(iniciar.await(10, TimeUnit.SECONDS)).isTrue(); avance("RECIBIDA", ""); return null; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var primera = pool.submit(tarea); var segunda = pool.submit(tarea);
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue(); iniciar.countDown();
            primera.get(20, TimeUnit.SECONDS); segunda.get(20, TimeUnit.SECONDS);
        }
        assertThat(eventos().stream().filter(evento -> "RECIBIDA".equals(evento.getAccion()))).hasSize(1);
        assertThat(auditorias("RECIBIDA")).isEqualTo(1); assertThat(avisos()).isEqualTo(avisosAntes + 1);
    }

    @Test void notasSinFirmaCerradasYAnuladasSoloConservanSuHistorial() {
        crear(); rechazarAvance(); servicio.oficializar(nota.getId(), creador); rechazarAvance();
        servicio.firmar(nota.getId(), creador); servicio.cerrar(nota.getId(), creador); rechazarAvance();
        servicio.anular(nota.getId(), "Motivo válido de anulación", creador); rechazarAvance();
    }

    @Test void crearConPdfSeRechazaSinOcuparElNumeroNiGuardarArchivos() {
        autorizarPdf();
        NotaCreateRequest request = new NotaCreateRequest(); request.setNumeroId(numero.getId());
        request.setSeguimientoUsuarioId(responsable.getId()); request.setAsunto("Borrador sin adjuntos");
        assertThatThrownBy(() -> controller.crear(request, pdf())).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("firma del receptor");
        assertThat(numeros.findById(numero.getId()).orElseThrow().getEstado()).isEqualTo("RESERVADO");
        assertThat(jdbc.queryForObject("select count(*) from notas where numero_id = ?", Integer.class, numero.getId())).isZero();
        verifyNoInteractions(archivos);
    }

    @Test void pdfSoloDespuesDeFirmaDelReceptorConVersionesYPermisosExistentes() {
        crear(); autorizarPdf();
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(false);
        assertThatThrownBy(() -> controller.adjuntarPdf(nota.getId(), pdf())).isInstanceOf(IllegalStateException.class);
        firmar();
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(false);
        assertThatThrownBy(() -> controller.adjuntarPdf(nota.getId(), pdf())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(archivos);
        avance("FIRMADA_RECEPTOR", "Firma física del receptor confirmada");
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(true);
        when(archivos.guardar(any())).thenReturn(new NotaPdfStorageService.StoredPdf("receptor.pdf", "test-only/receptor.pdf", "application/pdf", 8L));
        controller.adjuntarPdf(nota.getId(), pdf()); controller.adjuntarPdf(nota.getId(), pdf());
        assertThat(documentos.listar(nota.getId())).extracting(NotaPdf::getNumeroVersion).containsExactly(2, 1);
        assertThat(eventos()).extracting(NotaHistorial::getAccion).containsSubsequence("FIRMAR", "FIRMADA_RECEPTOR", "ADJUNTAR_PDF", "REEMPLAZAR_PDF");
        assertThat(jdbc.queryForObject("select seguimiento_usuario_id from notas where id = ?", Long.class, nota.getId())).isEqualTo(responsable.getId());
    }

    @Test void firmaDelReceptorConRollbackNoHabilitaLaCarga() {
        crear(); firmar(); autorizarPdf();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            avance("FIRMADA_RECEPTOR", "Registro que no debe confirmarse"); throw new IllegalStateException("Fallo posterior");
        })).hasMessageContaining("posterior");
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(false);
        assertThatThrownBy(() -> controller.adjuntarPdf(nota.getId(), pdf())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(archivos);
    }

    @Test void firmaDelReceptorNoConcedePermisoDeAdjuntar() {
        crear(); firmar(); avance("FIRMADA_RECEPTOR", "Firma física confirmada");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            creador.getCorreo(), "", List.of(new SimpleGrantedAuthority("Notas:Ver"))));
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(false);
        assertThatThrownBy(() -> controller.adjuntarPdf(nota.getId(), pdf())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(archivos);
    }

    @Test void seguimientoPosteriorYCierreConservanLaFirmaPeroAnularBloqueaNuevasCargas() {
        crear(); firmar(); avance("FIRMADA_RECEPTOR", "Firma física confirmada");
        avance("RESPONDIDA", "Respuesta posterior a la firma"); autorizarPdf();
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(true);
        servicio.cerrar(nota.getId(), creador);
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(true);
        when(archivos.guardar(any())).thenReturn(new NotaPdfStorageService.StoredPdf("receptor.pdf", "test-only/receptor.pdf", "application/pdf", 8L));
        controller.adjuntarPdf(nota.getId(), pdf());
        servicio.anular(nota.getId(), "Motivo de prueba para anular", creador);
        assertThat(controller.obtener(nota.getId()).get("puedeAdjuntarPdf")).isEqualTo(false);
        assertThatThrownBy(() -> controller.adjuntarPdf(nota.getId(), pdf())).isInstanceOf(IllegalStateException.class);
        assertThat(documentos.listar(nota.getId())).hasSize(1);
        verify(archivos, times(1)).guardar(any());
    }

    @Test void notaExistenteFirmadaSinEventoDeSeguimientoPuedeContinuarSinReescribirSuHistoria() {
        crear();
        jdbc.update("update notas set estado = 'FIRMADA', firmada_por_id = ?, firmada_at = ? where id = ?",
            creador.getId(), LocalDateTime.now(), nota.getId());
        List<Long> anteriores = eventos().stream().map(NotaHistorial::getId).toList();
        avance("RECIBIDA", "Recepción registrada después de actualizar el sistema");
        assertThat(eventos().subList(0, anteriores.size())).extracting(NotaHistorial::getId).containsExactlyElementsOf(anteriores);
        assertThat(eventos().getLast().getAccion()).isEqualTo("RECIBIDA");
    }

    @Test void migracionAgregaPermisosAlCatalogoExistenteYConservaLosDeFirmaYAnulacion() {
        assertThat(jdbc.queryForObject("select count(*) from permisos where modulo = 'Notas' and accion in ('Dar seguimiento', 'Cambiar seguimiento')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from permisos where modulo = 'Notas' and accion in ('Firmar', 'Anular')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from rol_permisos rp join permisos p on p.id=rp.permiso_id where p.modulo='Notas' and p.accion='Editar' and not exists (select 1 from rol_permisos nuevo join permisos pn on pn.id=nuevo.permiso_id where nuevo.rol_id=rp.rol_id and pn.modulo='Notas' and pn.accion='Cambiar seguimiento')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from roles r cross join permisos p where lower(trim(r.nombre)) in ('superadmin','administrador') and p.modulo='Notas' and p.accion in ('Dar seguimiento','Cambiar seguimiento') and p.recurso='Nota' and not exists (select 1 from rol_permisos rp where rp.rol_id=r.id and rp.permiso_id=p.id)", Integer.class)).isZero();
    }

    private Usuario usuario(String nombre) {
        String key = "nota-traza-" + UUID.randomUUID(); Usuario usuario = new Usuario();
        usuario.setNombre(nombre); usuario.setApellido("Prueba"); usuario.setCorreo(key + "@example.test");
        usuario.setUsername(key); usuario.setPassword("test-only"); usuario = usuarios.saveAndFlush(usuario);
        usuarioIds.add(usuario.getId()); return usuario;
    }
    private void autorizarPdf() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            creador.getCorreo(), "", List.of(new SimpleGrantedAuthority("Notas:Crear"),
                new SimpleGrantedAuthority("Notas:Ver"), new SimpleGrantedAuthority("Notas:Adjuntar PDF"))));
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("pdf", "receptor.pdf", "application/pdf", "%PDF-1.7".getBytes()); }
    private void crear() {
        NotaCreateRequest request = new NotaCreateRequest(); request.setNumeroId(numero.getId());
        request.setAsunto("Seguimiento de prueba"); request.setDestino(institucion.getNombre());
        request.setInstitucionId(institucion.getId()); request.setDestinatarioNombre("Receptor de prueba");
        request.setDestinatarioCargo("Responsable de recepción"); request.setContenido("Contenido institucional");
        request.setDescripcion("Comentario inicial"); request.setSeguimientoUsuarioId(responsable.getId());
        nota = servicio.crear(request, null, creador, false);
    }
    private NotaUpdateRequest actualizacion() {
        NotaUpdateRequest request = new NotaUpdateRequest(); request.setAsunto(nota.getAsunto());
        request.setDestino(nota.getDestino()); request.setSeguimientoUsuarioId(responsable.getId()); return request;
    }
    private void firmar() { servicio.oficializar(nota.getId(), creador); servicio.firmar(nota.getId(), creador); }
    private void avance(String estado, String comentario) {
        servicio.seguimiento(nota.getId(), new NotaSeguimientoRequest(estado, comentario), creador, true, EnumSet.allOf(NotaSeguimientoEstado.class));
    }
    private void rechazarAvance() {
        List<Long> antes = eventos().stream().map(NotaHistorial::getId).toList();
        assertThatThrownBy(() -> avance("RECIBIDA", "No debe registrarse")).isInstanceOf(IllegalStateException.class);
        assertThat(eventos()).extracting(NotaHistorial::getId).containsExactlyElementsOf(antes);
    }
    private List<NotaHistorial> eventos() { return historial.findByNotaIdOrderByCreatedAtAscIdAsc(nota.getId()); }
    private int auditorias(String accion) { return jdbc.queryForObject("select count(*) from audit_logs where entidad='nota' and entidad_id=? and accion=?", Integer.class, nota.getId(), accion); }
    private int avisos() { return jdbc.queryForObject("select count(*) from notificaciones where usuario_id in (?, ?)", Integer.class, responsable.getId(), nuevoResponsable.getId()); }
    private static void autenticar(Usuario usuario) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getCorreo(), "", List.of())); }
}
