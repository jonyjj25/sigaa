package com.sigaa.service;

import com.sigaa.dto.NotaCreateRequest;
import com.sigaa.dto.NotaUpdateRequest;
import com.sigaa.dto.NotaVistaPreviaRequest;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotaDepartamentoServiceTest {
    private final NotaRepository notas = mock(NotaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final OrganizationRepository instituciones = mock(OrganizationRepository.class);
    private final OrganizationDepartmentRepository departamentos = mock(OrganizationDepartmentRepository.class);
    private final NotaNumeroService numeros = mock(NotaNumeroService.class);
    private final NotaTrazabilidadService trazabilidad = mock(NotaTrazabilidadService.class);
    private final InternalNotificationService avisos = mock(InternalNotificationService.class);
    private final NotaService service = new NotaService(notas, usuarios, instituciones, numeros,
        mock(NotaRelacionService.class), trazabilidad, avisos, departamentos);
    private Usuario creador;
    private Usuario seguimiento;
    private Organization institucion;
    private OrganizationDepartment departamento;
    private Nota nota;

    @BeforeEach void preparar() {
        creador = new Usuario(); creador.setId(1L);
        seguimiento = new Usuario(); seguimiento.setId(2L); seguimiento.setActivo(true);
        institucion = new Organization(); institucion.setId(3L); institucion.setNombre("Institución de prueba"); institucion.setActivo(true);
        departamento = departamento(4L, "Área de prueba", "Destinatario externo", "Jefatura del área");
        NotaNumero numero = new NotaNumero(); numero.setId(5L); numero.setNumero("N-005"); numero.setEstado("OCUPADO");
        nota = new Nota(); nota.setId(6L); nota.setEstado("BORRADOR"); nota.setNumero(numero);
        nota.setAsunto("Asunto de prueba"); nota.setFechaOficial(LocalDate.of(2026, 10, 1));
        nota.setCreadoPor(creador); nota.setSeguimientoUsuario(seguimiento);
        nota.setInstitucion(institucion); nota.setDestino("Institución guardada");
        nota.setDepartamento(departamento); nota.setDepartamentoNombre("Área guardada");
        nota.setDestinatarioNombre("Destinatario guardado"); nota.setDestinatarioCargo("Cargo guardado");
        when(usuarios.findById(2L)).thenReturn(Optional.of(seguimiento));
        when(instituciones.findById(3L)).thenReturn(Optional.of(institucion));
        when(departamentos.findByIdAndOrganizationId(4L, 3L)).thenReturn(Optional.of(departamento));
        when(numeros.consumirReserva(5L, creador)).thenReturn(new NotaNumeroService.ReservaConsumida(numero, creador, LocalDateTime.now()));
        when(notas.saveAndFlush(any())).thenAnswer(call -> { Nota creada = call.getArgument(0); creada.setId(6L); return creada; });
        when(notas.findByIdForUpdate(6L)).thenReturn(Optional.of(nota));
        when(notas.findById(6L)).thenReturn(Optional.of(nota));
        when(notas.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void creaConDatosDelDepartamentoYNotificaAlUsuarioInternoIndependiente() {
        Nota creada = service.crear(creacion(), null, creador, false);
        assertThat(creada.getDepartamento()).isSameAs(departamento);
        assertThat(creada.getDepartamentoNombre()).isEqualTo("Área de prueba");
        assertThat(creada.getDestinatarioNombre()).isEqualTo("Destinatario externo");
        assertThat(creada.getDestinatarioCargo()).isEqualTo("Jefatura del área");
        assertThat(creada.getSeguimientoUsuario()).isSameAs(seguimiento);
        verify(avisos).send(eq("nota_asignada"), same(seguimiento), anyString(), anyString());
        verify(usuarios, times(1)).findById(2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ajeno", "inexistente", "inactivo", "institucion_inactiva", "sin_institucion"})
    void rechazaCombinacionesInvalidasAntesDeGuardarOAvisar(String caso) {
        NotaCreateRequest request = creacion();
        if (caso.equals("ajeno") || caso.equals("inexistente")) when(departamentos.findByIdAndOrganizationId(4L, 3L)).thenReturn(Optional.empty());
        if (caso.equals("inactivo")) departamento.setActivo(false);
        if (caso.equals("institucion_inactiva")) institucion.setActivo(false);
        if (caso.equals("sin_institucion")) request.setInstitucionId(null);
        assertThatThrownBy(() -> service.crear(request, null, creador, false)).isInstanceOf(IllegalArgumentException.class);
        verify(notas, never()).saveAndFlush(any()); verifyNoInteractions(avisos, trazabilidad);
    }

    @Test void conservarSeleccionNoActualizaLosValoresHistoricosNiEmiteAvisos() {
        institucion.setNombre("Institución renombrada"); institucion.setActivo(false);
        departamento.setNombre("Área renombrada"); departamento.setDirector("Nueva persona externa"); departamento.setActivo(false);
        service.actualizar(6L, actualizacion(4L), creador);
        assertThat(nota.getInstitucionNombreGuardado()).isEqualTo("Institución guardada");
        assertThat(nota.getDepartamentoNombre()).isEqualTo("Área guardada");
        assertThat(nota.getDestinatarioNombre()).isEqualTo("Destinatario guardado");
        assertThat(nota.getDestinatarioCargo()).isEqualTo("Cargo guardado");
        assertThat(nota.getSeguimientoUsuario()).isSameAs(seguimiento);
        verifyNoInteractions(departamentos, avisos, trazabilidad);
    }

    @Test void cambiarDepartamentoCopiaSusDatosSinReasignarSeguimiento() {
        OrganizationDepartment nuevo = departamento(7L, "Área nueva", "Otra persona externa", "Otro cargo");
        when(departamentos.findByIdAndOrganizationId(7L, 3L)).thenReturn(Optional.of(nuevo));
        service.actualizar(6L, actualizacion(7L), creador);
        assertThat(nota.getDepartamento()).isSameAs(nuevo);
        assertThat(nota.getDepartamentoNombre()).isEqualTo("Área nueva");
        assertThat(nota.getDestinatarioNombre()).isEqualTo("Otra persona externa");
        assertThat(nota.getDestinatarioCargo()).isEqualTo("Otro cargo");
        assertThat(nota.getSeguimientoUsuario()).isSameAs(seguimiento);
        verify(trazabilidad).historial(nota, creador, "ACTUALIZAR", "BORRADOR", "BORRADOR", "Se actualizó el mismo borrador.");
        verify(trazabilidad, never()).historial(any(), any(), eq("CAMBIAR_RESPONSABLE"), any(), any(), any());
        verifyNoInteractions(avisos);
    }

    @Test void puedeLimpiarLaCadenaExplicitamenteSinCambiarSeguimiento() {
        NotaUpdateRequest request = actualizacion(null); request.setInstitucionId(null);
        request.setDestino(""); request.setDestinatarioNombre(""); request.setDestinatarioCargo("");
        service.actualizar(6L, request, creador);
        assertThat(nota.getInstitucion()).isNull(); assertThat(nota.getDepartamento()).isNull();
        assertThat(nota.getDepartamentoNombre()).isNull(); assertThat(nota.getDestinatarioNombre()).isEmpty();
        assertThat(nota.getDestinatarioCargo()).isEmpty(); assertThat(nota.getDestino()).isEmpty();
        assertThat(nota.getSeguimientoUsuario()).isSameAs(seguimiento); verifyNoInteractions(avisos);
    }

    @Test void clienteAnteriorQueOmiteDepartamentoConservaElVinculoGuardado() throws Exception {
        NotaUpdateRequest request = new ObjectMapper().readValue("""
            {"asunto":"Asunto de prueba","seguimientoUsuarioId":2}
            """, NotaUpdateRequest.class);
        assertThat(request.isDepartamentoDefinido()).isFalse();
        service.actualizar(6L, request, creador);
        assertThat(nota.getDepartamento()).isSameAs(departamento);
        assertThat(nota.getDepartamentoNombre()).isEqualTo("Área guardada"); verifyNoInteractions(avisos);
        NotaUpdateRequest limpiar = new ObjectMapper().readValue("{\"departamentoId\":null}", NotaUpdateRequest.class);
        assertThat(limpiar.isDepartamentoDefinido()).isTrue();
    }

    @Test void actualizarConDepartamentoAjenoNoModificaNingunCampo() {
        when(departamentos.findByIdAndOrganizationId(8L, 3L)).thenReturn(Optional.empty());
        NotaUpdateRequest request = actualizacion(8L); request.setDestinatarioNombre("Otro nombre");
        assertThatThrownBy(() -> service.actualizar(6L, request, creador)).isInstanceOf(IllegalArgumentException.class);
        assertThat(nota.getDepartamento()).isSameAs(departamento);
        assertThat(nota.getDestinatarioNombre()).isEqualTo("Destinatario guardado");
        assertThat(nota.getSeguimientoUsuario()).isSameAs(seguimiento);
        verify(notas, never()).save(any()); verifyNoInteractions(avisos, trazabilidad);
    }

    @Test void catalogoConsultaSoloDepartamentosActivosDeLaInstitucionElegida() {
        when(departamentos.findByOrganizationIdAndActivoTrueOrderByNombreAsc(3L)).thenReturn(List.of(departamento));
        assertThat(service.departamentosActivos(3L)).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(4L); assertThat(item.director()).isEqualTo("Destinatario externo");
        });
        verify(departamentos).findByOrganizationIdAndActivoTrueOrderByNombreAsc(3L);
        verify(departamentos, never()).findAll(); verifyNoInteractions(usuarios);
    }

    @Test void vistaPreviaConservaInstitucionGuardadaYPermiteLimpiarlaSinPersistir() {
        institucion.setNombre("Nombre actual del catálogo");
        Nota copia = service.prepararVistaPrevia(new NotaVistaPreviaRequest(6L, null, 3L, null, null, null, null, null, true), creador);
        assertThat(copia.getInstitucionNombreGuardado()).isEqualTo("Institución guardada");
        Nota vacia = service.prepararVistaPrevia(new NotaVistaPreviaRequest(6L, null, null, null, null, null, null, null, true), creador);
        assertThat(vacia.getInstitucion()).isNull(); assertThat(vacia.getDestino()).isEmpty();
        assertThat(nota.getInstitucion()).isSameAs(institucion); verify(notas, never()).save(any());
    }

    @Test void noOficializaUnBorradorConDepartamentoDesactivado() {
        departamento.setActivo(false); nota.setContenido("Contenido de prueba");
        assertThatThrownBy(() -> service.oficializar(6L, creador)).hasMessageContaining("departamento activo");
        assertThat(nota.getEstado()).isEqualTo("BORRADOR"); verifyNoInteractions(avisos, trazabilidad);
    }

    private OrganizationDepartment departamento(Long id, String nombre, String director, String cargo) {
        OrganizationDepartment item = new OrganizationDepartment(); ReflectionTestUtils.setField(item, "id", id);
        item.setOrganization(institucion); item.setNombre(nombre); item.setDirector(director);
        item.setDirectorCargo(cargo); item.setActivo(true); return item;
    }
    private NotaCreateRequest creacion() {
        NotaCreateRequest request = new NotaCreateRequest(); request.setNumeroId(5L); request.setAsunto("Asunto de prueba");
        request.setInstitucionId(3L); request.setDepartamentoId(4L); request.setSeguimientoUsuarioId(2L); return request;
    }
    private NotaUpdateRequest actualizacion(Long departamentoId) {
        NotaUpdateRequest request = new NotaUpdateRequest(); request.setAsunto("Asunto de prueba");
        request.setInstitucionId(3L); request.setDepartamentoId(departamentoId); request.setSeguimientoUsuarioId(2L); return request;
    }
}
