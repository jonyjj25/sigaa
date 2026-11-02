package com.sigaa.service;

import com.sigaa.dto.NotaCreateRequest;
import com.sigaa.dto.NotaUpdateRequest;
import com.sigaa.dto.NotaVistaPreviaRequest;
import com.sigaa.model.Nota;
import com.sigaa.model.NotaNumero;
import com.sigaa.model.Organization;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaRepository;
import com.sigaa.repository.OrganizationRepository;
import com.sigaa.repository.OrganizationDepartmentRepository;
import com.sigaa.repository.UsuarioRepository;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotaServiceTest {
    @Test
    void listadoPaginaEnBackendEscapaBusquedaLiteralYReutilizaConteoDeResumen() {
        NotaRepository notas = mock(NotaRepository.class);
        NotaService service = new NotaService(notas, mock(UsuarioRepository.class), mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class),
            mock(NotaTrazabilidadService.class), mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        when(notas.resumirFiltradas(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
            anyString(), any(), any(), any(), any(), anyString(), anyString(), anyLong(), anyBoolean()))
            .thenReturn(List.<Object[]>of(new Object[]{"BORRADOR", 52L}, new Object[]{"ANULADA", 3L}));

        NotaService.Listado resultado = service.listado("  50%_\\  ", "", "", "", null, null,
            null, null, "", -1, 100, 9L, false);

        assertThat(resultado.total()).isEqualTo(55);
        assertThat(resultado.page()).isZero(); assertThat(resultado.size()).isEqualTo(50);
        assertThat(resultado.totalPages()).isEqualTo(2);
        assertThat(resultado.resumen()).containsEntry("BORRADOR", 52L).containsEntry("ANULADA", 3L);
        verify(notas).listarFiltradas(eq("50%_\\"), eq("%50\\%\\_\\\\%"), eq(""), eq("%%"), eq(""), eq("%%"),
            eq(""), isNull(), isNull(), isNull(), isNull(), eq(""), eq("%%"), eq(9L), eq(false),
            eq(org.springframework.data.domain.PageRequest.of(0, 50)));
        verify(notas, never()).count();
    }

    @Test
    void cambiaFechaDelBorradorYRegistraValoresEnHistorialYAuditoria() {
        NotaRepository notas = mock(NotaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        NotaService service = new NotaService(notas, usuarios, mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class), trazabilidad, mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Usuario actual = new Usuario(); actual.setId(1L);
        Nota nota = borradorConFecha(actual);
        when(notas.findByIdForUpdate(10L)).thenReturn(Optional.of(nota));
        when(usuarios.findById(1L)).thenReturn(Optional.of(actual));
        when(notas.save(nota)).thenReturn(nota);
        NotaUpdateRequest request = solicitudConFecha(LocalDate.of(2026, 10, 1));

        assertThat(service.actualizar(10L, request, actual).getFechaOficial()).isEqualTo(request.getFechaOficial());
        String detalle = "Fecha de la nota modificada: 2026-09-28 → 2026-10-01.";
        verify(trazabilidad).historial(nota, actual, "CAMBIAR_FECHA", "BORRADOR", "BORRADOR", detalle);
        verify(trazabilidad).audit("CAMBIAR_FECHA", 10L, detalle);
        verify(trazabilidad, never()).historial(any(), any(), eq("ACTUALIZAR"), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_SEGUIMIENTO", "FIRMADA", "CERRADA", "ANULADA", "BORRADOR_OFICIALIZADO"})
    void rechazaCambioDeFechaSiLaNotaYaSeOficializo(String estado) {
        NotaRepository notas = mock(NotaRepository.class);
        NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        NotaService service = new NotaService(notas, mock(UsuarioRepository.class), mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class), trazabilidad, mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Usuario actual = new Usuario(); actual.setId(1L);
        Nota nota = borradorConFecha(actual);
        nota.setEstado(estado.equals("BORRADOR_OFICIALIZADO") ? "BORRADOR" : estado);
        nota.setOficializadaAt(LocalDateTime.of(2026, 9, 28, 12, 0));
        when(notas.findByIdForUpdate(10L)).thenReturn(Optional.of(nota));

        assertThatThrownBy(() -> service.actualizar(10L, solicitudConFecha(LocalDate.of(2026, 10, 1)), actual))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("antes de oficializar");
        assertThat(nota.getFechaOficial()).isEqualTo(LocalDate.of(2026, 9, 28));
        verify(notas, never()).save(any());
        verifyNoInteractions(trazabilidad);
    }

    @Test
    void noRegistraCambioDeFechaSiSeOmiteOSeEnviaLaMisma() {
        NotaRepository notas = mock(NotaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        NotaService service = new NotaService(notas, usuarios, mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class), trazabilidad, mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Usuario actual = new Usuario(); actual.setId(1L);
        Nota nota = borradorConFecha(actual);
        when(notas.findByIdForUpdate(10L)).thenReturn(Optional.of(nota));
        when(usuarios.findById(1L)).thenReturn(Optional.of(actual));
        when(notas.save(nota)).thenReturn(nota);

        service.actualizar(10L, solicitudConFecha(null), actual);
        service.actualizar(10L, solicitudConFecha(nota.getFechaOficial()), actual);

        assertThat(nota.getFechaOficial()).isEqualTo(LocalDate.of(2026, 9, 28));
        verifyNoInteractions(trazabilidad);
    }

    private Nota borradorConFecha(Usuario seguimiento) {
        Nota nota = new Nota(); nota.setId(10L); nota.setEstado("BORRADOR");
        nota.setAsunto("Asunto"); nota.setDestino("Destino"); nota.setSeguimientoUsuario(seguimiento);
        nota.setFechaOficial(LocalDate.of(2026, 9, 28));
        return nota;
    }

    private NotaUpdateRequest solicitudConFecha(LocalDate fecha) {
        NotaUpdateRequest request = new NotaUpdateRequest(); request.setAsunto("Asunto"); request.setDestino("Destino");
        request.setSeguimientoUsuarioId(1L); request.setFechaOficial(fecha);
        return request;
    }

    @Test
    void vistaDeBorradorReflejaEdicionesYDirectorActualSinGuardarNiCambiarElOriginal() {
        NotaRepository notas = mock(NotaRepository.class); UsuarioRepository usuarios = mock(UsuarioRepository.class);
        NotaService service = new NotaService(notas, usuarios, mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class), mock(NotaTrazabilidadService.class), mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Nota original = NotaWordServiceTest.notaEjemplo(); original.setEstado("BORRADOR");
        original.setFirmanteNombre(null); original.setFirmanteCargo(null);
        when(notas.findById(10L)).thenReturn(Optional.of(original));
        Usuario directora = new Usuario(); directora.setNombre("Diana"); directora.setApellido("López");
        directora.setCargo("Subcomisionada"); directora.setInstitucion("Institución configurada");
        when(usuarios.findActiveDirectors()).thenReturn(List.of(directora));
        Nota copia = service.prepararVistaPrevia(new NotaVistaPreviaRequest(10L, null, null,
            LocalDate.of(2026, 10, 7), "Asunto editado", "María", "Jefa", "Texto editado"), new Usuario());
        assertThat(copia).isNotSameAs(original);
        assertThat(copia.getContenido()).isEqualTo("Texto editado");
        assertThat(copia.getFechaOficial()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(copia.getFirmanteNombre()).isEqualTo("Diana López");
        assertThat(copia.getFirmanteCargo()).isEqualTo("Subcomisionada");
        assertThat(copia.getCreadoPor()).isSameAs(original.getCreadoPor());
        assertThat(original.getContenido()).contains("Solicitamos");
        assertThat(original.getFirmanteNombre()).isNull();
        verify(notas, never()).save(any()); verify(notas, never()).saveAndFlush(any());
    }

    @Test
    void vistaOficialIgnoraEdicionesDelClienteYConservaLaFirmaHistorica() {
        NotaRepository notas = mock(NotaRepository.class); UsuarioRepository usuarios = mock(UsuarioRepository.class);
        NotaService service = new NotaService(notas, usuarios, mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class), mock(NotaTrazabilidadService.class), mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Nota original = NotaWordServiceTest.notaEjemplo();
        when(notas.findById(10L)).thenReturn(Optional.of(original));
        Nota copia = service.prepararVistaPrevia(new NotaVistaPreviaRequest(10L, 99L, null,
            LocalDate.of(2027, 1, 1), "Asunto falso", "Falso", "Falso", "Contenido falso"), new Usuario());
        assertThat(copia.getContenido()).isEqualTo(original.getContenido());
        assertThat(copia.getFechaOficial()).isEqualTo(original.getFechaOficial());
        assertThat(copia.getAsunto()).isEqualTo(original.getAsunto());
        assertThat(copia.getNumero()).isSameAs(original.getNumero());
        assertThat(copia.getFirmanteNombre()).isEqualTo("Ana María de la Cruz");
        assertThat(copia.getFirmanteCargo()).isEqualTo("Comisionada");
        verifyNoInteractions(usuarios); verify(notas, never()).save(any());
    }

    @Test
    void noPermiteCambiarFechaAOtroAnioConUnConsecutivoAnual() {
        NotaRepository notas = mock(NotaRepository.class);
        NotaService service = new NotaService(notas, mock(UsuarioRepository.class), mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class), mock(NotaTrazabilidadService.class), mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Nota original = NotaWordServiceTest.notaEjemplo(); original.setEstado("BORRADOR");
        when(notas.findByIdForUpdate(10L)).thenReturn(Optional.of(original));
        when(notas.findById(10L)).thenReturn(Optional.of(original));
        assertThatThrownBy(() -> service.actualizar(10L, solicitudConFecha(LocalDate.of(2027, 1, 1)), new Usuario()))
            .hasMessageContaining("coincidir");
        assertThatThrownBy(() -> service.prepararVistaPrevia(new NotaVistaPreviaRequest(10L, null, null,
            LocalDate.of(2027, 1, 1), null, null, null, null), new Usuario())).hasMessageContaining("coincidir");
        assertThat(original.getFechaOficial()).isEqualTo(LocalDate.of(2026, 10, 6));
        verify(notas, never()).save(any());
    }

    @Test
    void creaNotaSinPdfYConsumeLaReserva() {
        NotaRepository notas = mock(NotaRepository.class); UsuarioRepository usuarios = mock(UsuarioRepository.class);
        OrganizationRepository organizaciones = mock(OrganizationRepository.class);
        NotaNumeroService numeros = mock(NotaNumeroService.class);
        NotaRelacionService relaciones = mock(NotaRelacionService.class); NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        InternalNotificationService notificaciones = mock(InternalNotificationService.class);
        NotaService service = new NotaService(notas, usuarios, organizaciones, numeros, relaciones, trazabilidad, notificaciones, mock(OrganizationDepartmentRepository.class));
        Usuario creador = new Usuario(); creador.setId(1L); Usuario seguimiento = new Usuario(); seguimiento.setId(2L);
        NotaNumero numero = new NotaNumero(); numero.setId(4L); numero.setNumero("N-004"); numero.setEstado("OCUPADO");
        when(numeros.consumirReserva(4L, creador)).thenReturn(new NotaNumeroService.ReservaConsumida(numero, creador, LocalDateTime.now().minusMinutes(1)));
        when(usuarios.findById(2L)).thenReturn(Optional.of(seguimiento));
        when(notas.saveAndFlush(any())).thenAnswer(invocation -> { Nota nota = invocation.getArgument(0); nota.setId(10L); return nota; });
        NotaCreateRequest request = new NotaCreateRequest(); request.setNumeroId(4L); request.setAsunto("Asunto");
        request.setDestino("Destino"); request.setSeguimientoUsuarioId(2L); request.setSensitiva(true);

        Nota creada = service.crear(request, null, creador, false);

        assertThat(creada.getPdfRuta()).isNull();
        assertThat(creada.getEstado()).isEqualTo("BORRADOR");
        assertThat(creada.getFechaOficial()).isNotNull();
        assertThat(creada.isSensitiva()).isTrue();
        verify(numeros).consumirReserva(4L, creador);
        verify(notificaciones).send(eq("nota_asignada"), same(seguimiento), contains("OAT-004-"),
            argThat(mensaje -> mensaje.contains("registro 10") && mensaje.contains("Asunto")));
    }

    @Test
    void notificaSoloCuandoCambiaElResponsableYNoAlGuardarLosMismosDatos() {
        NotaRepository notas = mock(NotaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        InternalNotificationService notificaciones = mock(InternalNotificationService.class);
        NotaService service = new NotaService(notas, usuarios, mock(OrganizationRepository.class),
            mock(NotaNumeroService.class), mock(NotaRelacionService.class),
            mock(NotaTrazabilidadService.class), notificaciones, mock(OrganizationDepartmentRepository.class));
        Usuario anterior = new Usuario(); anterior.setId(1L);
        Usuario nuevo = new Usuario(); nuevo.setId(2L);
        Nota nota = borradorConFecha(anterior);
        NotaNumero numero = new NotaNumero(); numero.setNumero("N-010"); nota.setNumero(numero);
        when(notas.findByIdForUpdate(10L)).thenReturn(Optional.of(nota));
        when(notas.save(nota)).thenReturn(nota);
        when(usuarios.findById(1L)).thenReturn(Optional.of(anterior));
        when(usuarios.findById(2L)).thenReturn(Optional.of(nuevo));
        NotaUpdateRequest request = solicitudConFecha(null);

        service.actualizar(10L, request, anterior);
        request.setAsunto("Asunto editado");
        service.actualizar(10L, request, anterior);
        verifyNoInteractions(notificaciones);
        request.setSeguimientoUsuarioId(2L);
        service.actualizar(10L, request, anterior);
        service.actualizar(10L, request, anterior);

        verify(notificaciones, times(1)).send(eq("nota_asignada"), same(nuevo), contains("OAT-010-2026"),
            contains("Asunto editado"));
        assertThat(nota.getSeguimientoUsuario()).isSameAs(nuevo);
        assertThat(nota.getNumero()).isSameAs(numero);
    }

    @Test
    void rechazaPdfAlCrearSinConsumirReservaNiGuardarONotificar() {
        NotaRepository notas = mock(NotaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        NotaNumeroService numeros = mock(NotaNumeroService.class);
        InternalNotificationService notificaciones = mock(InternalNotificationService.class);
        NotaService service = new NotaService(notas, usuarios, mock(OrganizationRepository.class), numeros,
            mock(NotaRelacionService.class), mock(NotaTrazabilidadService.class), notificaciones, mock(OrganizationDepartmentRepository.class));
        Usuario creador = new Usuario(); creador.setId(1L);
        var pdf = new org.springframework.mock.web.MockMultipartFile("pdf", "nota.pdf", "application/pdf", new byte[]{1});
        NotaCreateRequest request = new NotaCreateRequest(); request.setNumeroId(10L);
        request.setSeguimientoUsuarioId(1L); request.setAsunto("Asunto");

        assertThatThrownBy(() -> service.crear(request, pdf, creador, false)).hasMessageContaining("firma del receptor");
        verifyNoInteractions(numeros, usuarios, notas, notificaciones);
    }

    @Test
    void rechazaModificacionesDeDatosYSeguimientoDespuesDeOficializar() {
        NotaRepository notas = mock(NotaRepository.class); UsuarioRepository usuarios = mock(UsuarioRepository.class);
        OrganizationRepository organizaciones = mock(OrganizationRepository.class);
        NotaNumeroService numeros = mock(NotaNumeroService.class);
        NotaRelacionService relaciones = mock(NotaRelacionService.class); NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        NotaService service = new NotaService(notas, usuarios, organizaciones, numeros, relaciones, trazabilidad, mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Usuario actual = new Usuario(); actual.setId(1L);
        Usuario seguimientoAnterior = new Usuario(); seguimientoAnterior.setId(2L);
        Usuario seguimientoNuevo = new Usuario(); seguimientoNuevo.setId(3L);
        NotaNumero numero = new NotaNumero(); numero.setNumero("N-004");
        Nota nota = new Nota(); nota.setId(10L); nota.setNumero(numero); nota.setAsunto("Asunto"); nota.setDestino("Destino");
        nota.setDescripcion("Descripción original"); nota.setSeguimientoUsuario(seguimientoAnterior); nota.setEstado("EN_SEGUIMIENTO");
        when(notas.findByIdForUpdate(10L)).thenReturn(Optional.of(nota));
        when(usuarios.findById(3L)).thenReturn(Optional.of(seguimientoNuevo));
        when(notas.save(nota)).thenReturn(nota);
        NotaUpdateRequest request = new NotaUpdateRequest(); request.setAsunto("Asunto actualizado"); request.setDestino("Destino");
        request.setSeguimientoUsuarioId(3L); request.setDescripcion("Nuevo seguimiento registrado"); request.setSensitiva(true);

        assertThatThrownBy(() -> service.actualizar(10L, request, actual))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("oficializada no puede modificarse");
        assertThat(nota.getDescripcion()).isEqualTo("Descripción original");
        assertThat(nota.getAsunto()).isEqualTo("Asunto");
        assertThat(nota.getSeguimientoUsuario()).isSameAs(seguimientoAnterior);
        verify(notas, never()).save(any());
        verifyNoInteractions(trazabilidad);
    }

    @Test
    void anulaLaNotaConMotivoYRegistraLaTrazabilidad() {
        NotaRepository notas = mock(NotaRepository.class); UsuarioRepository usuarios = mock(UsuarioRepository.class);
        OrganizationRepository organizaciones = mock(OrganizationRepository.class);
        NotaNumeroService numeros = mock(NotaNumeroService.class);
        NotaRelacionService relaciones = mock(NotaRelacionService.class); NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        NotaService service = new NotaService(notas, usuarios, organizaciones, numeros, relaciones, trazabilidad, mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Usuario actual = new Usuario(); actual.setId(1L);
        NotaNumero numero = new NotaNumero(); numero.setNumero("N-020");
        Nota nota = new Nota(); nota.setId(20L); nota.setNumero(numero); nota.setEstado("FIRMADA");
        when(notas.findByIdForUpdate(20L)).thenReturn(Optional.of(nota));
        when(notas.save(nota)).thenReturn(nota);

        Nota anulada = service.anular(20L, "Documento emitido por duplicado", actual);

        assertThat(anulada.getEstado()).isEqualTo("ANULADA");
        assertThat(anulada.getAnuladaPor()).isSameAs(actual);
        assertThat(anulada.getAnulacionMotivo()).isEqualTo("Documento emitido por duplicado");
        verify(trazabilidad).historial(nota, actual, "ANULAR", "FIRMADA", "ANULADA",
            "Motivo de anulación: Documento emitido por duplicado");
    }

    @Test
    void oficializaBorradorYConservaSnapshotDeDirectorODirectora() {
        NotaRepository notas = mock(NotaRepository.class); UsuarioRepository usuarios = mock(UsuarioRepository.class);
        OrganizationRepository organizaciones = mock(OrganizationRepository.class);
        NotaNumeroService numeros = mock(NotaNumeroService.class);
        NotaRelacionService relaciones = mock(NotaRelacionService.class); NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        NotaService service = new NotaService(notas, usuarios, organizaciones, numeros, relaciones, trazabilidad, mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Usuario actual = new Usuario(); actual.setId(1L);
        Usuario directora = new Usuario(); directora.setId(2L); directora.setNombre("Ana"); directora.setApellido("Pérez"); directora.setCargo("Director");
        directora.setInstitucion("Autoridad Nacional de Transparencia");
        Organization institucion = new Organization(); institucion.setId(3L); institucion.setNombre("Ministerio de Seguridad Pública"); institucion.setActivo(true);
        NotaNumero numero = new NotaNumero(); numero.setNumero("N-030"); numero.setEstado("OCUPADO");
        Nota nota = new Nota(); nota.setId(30L); nota.setNumero(numero); nota.setEstado("BORRADOR"); nota.setFechaOficial(LocalDate.of(2026, 9, 28));
        nota.setAsunto("Asunto"); nota.setDestinatarioNombre("Juan Pérez"); nota.setDestinatarioCargo("Comisionado");
        nota.setInstitucion(institucion); nota.setContenido("Contenido institucional");
        when(notas.findByIdForUpdate(30L)).thenReturn(Optional.of(nota));
        when(usuarios.findActiveDirectors()).thenReturn(List.of(directora));
        when(notas.save(nota)).thenReturn(nota);

        Nota oficializada = service.oficializar(30L, actual);

        assertThat(oficializada.getEstado()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(oficializada.getFirmanteNombre()).isEqualTo("Ana Pérez");
        assertThat(oficializada.getFirmanteCargo()).isEqualTo("Director");
        assertThat(oficializada.getFirmanteInstitucion()).isEqualTo("Autoridad Nacional de Transparencia");
        assertThat(oficializada.getFechaOficial()).isEqualTo(LocalDate.of(2026, 9, 28));
        verify(trazabilidad).historial(eq(nota), eq(actual), eq("OFICIALIZAR"), eq("BORRADOR"), eq("EN_SEGUIMIENTO"), contains("Ana Pérez"));
    }

    @Test
    void rechazaOficializacionSinDirectoraPeroNoAfectaBorrador() {
        NotaRepository notas = mock(NotaRepository.class); UsuarioRepository usuarios = mock(UsuarioRepository.class);
        OrganizationRepository organizaciones = mock(OrganizationRepository.class);
        NotaNumeroService numeros = mock(NotaNumeroService.class);
        NotaRelacionService relaciones = mock(NotaRelacionService.class); NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
        NotaService service = new NotaService(notas, usuarios, organizaciones, numeros, relaciones, trazabilidad, mock(InternalNotificationService.class), mock(OrganizationDepartmentRepository.class));
        Usuario actual = new Usuario(); actual.setId(1L);
        Organization institucion = new Organization(); institucion.setId(3L); institucion.setActivo(true);
        NotaNumero numero = new NotaNumero(); numero.setNumero("N-031"); numero.setEstado("OCUPADO");
        Nota nota = new Nota(); nota.setId(31L); nota.setNumero(numero); nota.setEstado("BORRADOR"); nota.setFechaOficial(LocalDate.now());
        nota.setAsunto("Asunto"); nota.setDestinatarioNombre("Destinatario"); nota.setDestinatarioCargo("Cargo");
        nota.setInstitucion(institucion); nota.setContenido("Contenido");
        when(notas.findByIdForUpdate(31L)).thenReturn(Optional.of(nota));
        when(usuarios.findActiveDirectors()).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.oficializar(31L, actual))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("No existe un Director o Directora activo");
        assertThat(nota.getEstado()).isEqualTo("BORRADOR");
        verify(notas, never()).save(nota);
    }
}
