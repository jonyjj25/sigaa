package com.sigaa.service;

import com.sigaa.dto.*;
import com.sigaa.model.AuditLog;
import com.sigaa.model.InventarioActivo;
import com.sigaa.model.OrgUnit;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.InventarioActivoRepository;
import com.sigaa.repository.OrgUnitRepository;
import com.sigaa.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventarioServiceTest {
    private InventarioActivoRepository repository;
    private AuditLogRepository audit;
    private InventarioService service;
    private UsuarioRepository usuarios;
    private OrgUnitRepository unidades;

    @BeforeEach void setup() {
        repository = mock(InventarioActivoRepository.class);
        audit = mock(AuditLogRepository.class);
        usuarios = mock(UsuarioRepository.class);
        unidades = mock(OrgUnitRepository.class);
        service = new InventarioService(repository, audit, usuarios, unidades);
    }

    private <T extends InventarioActivoRequest> T completar(T request) {
        request.setNombre("Silla de prueba");
        request.setCategoria("Mobiliario");
        request.setUbicacion("Oficina de prueba");
        request.setEstado("en buen estado");
        request.setTipoAsignacion("SIN_ASIGNAR");
        return request;
    }

    private InventarioActivo activo() {
        InventarioActivo activo = new InventarioActivo();
        activo.setId(1L);
        activo.setCodigo("INV-2025-012");
        activo.setNombre("Silla de prueba");
        activo.setCategoria("Mobiliario");
        activo.setUbicacion("Oficina de prueba");
        return activo;
    }

    private void guardar() {
        when(repository.saveAndFlush(any(InventarioActivo.class))).thenAnswer(invocation -> {
            InventarioActivo activo = invocation.getArgument(0);
            if (activo.getId() == null) activo.setId(1L);
            return activo;
        });
    }

    @Test void assignableUnitsComeFromTheActiveOrganizationStructure() {
        OrgUnit direccion = new OrgUnit();
        direccion.setId(21L);
        direccion.setNombre("Dirección Administrativa");
        direccion.setActivo(true);
        when(unidades.findByActivoTrueOrderByOrdenAscNombreAsc()).thenReturn(java.util.List.of(direccion));
        when(usuarios.findByActivoTrue()).thenReturn(java.util.List.of());

        var asignables = service.asignables();

        assertThat(asignables.get("unidades"))
                .containsExactly(new InventarioResponse.OpcionAsignacion(21L, "Dirección Administrativa"));
        verify(unidades).findByActivoTrueOrderByOrdenAscNombreAsc();
    }

    @Test void creationUsesExistingCodesWithoutScanningAssets() {
        guardar();
        when(repository.ultimoNumeroCodigo(anyString())).thenReturn(1233L);
        var request = completar(new InventarioCreateRequest());
        request.setNombre("  Silla de prueba  ");
        var response = service.crear(request);
        assertThat(response.codigo()).isEqualTo("INV-" + LocalDate.now().getYear() + "-1234");
        assertThat(response.nombre()).isEqualTo("Silla de prueba");
        assertThat(response.unidad()).isEmpty();
        assertThat(response.tipoAsignacion()).isEqualTo("SIN_ASIGNAR");
        assertThat(response.responsable()).isEmpty();
        var order = inOrder(repository);
        order.verify(repository).bloquearGeneracionCodigo();
        order.verify(repository).ultimoNumeroCodigo("INV-" + LocalDate.now().getYear() + "-");
        order.verify(repository).saveAndFlush(any(InventarioActivo.class));
        verify(repository, never()).findAll();
        verify(repository, never()).existsByCodigo(anyString());
        verify(audit).saveAndFlush(argThat(log -> "CREAR".equals(log.getAccion()) && log.getEntidadId().equals(1L)));
    }

    @Test void firstAndFollowingCodesKeepTheirPadding() {
        guardar();
        when(repository.ultimoNumeroCodigo(anyString())).thenReturn(0L, 2L);
        assertThat(service.crear(completar(new InventarioCreateRequest())).codigo()).endsWith("-001");
        assertThat(service.crear(completar(new InventarioCreateRequest())).codigo()).endsWith("-003");
    }

    @Test void exhaustedNumberingDoesNotSaveOrAudit() {
        when(repository.ultimoNumeroCodigo(anyString())).thenReturn(Long.MAX_VALUE);
        assertThatThrownBy(() -> service.crear(completar(new InventarioCreateRequest())))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void assignmentAndFunctioningChangesAreAuditedWithoutChangingCode() {
        guardar();
        var activo = activo();
        when(repository.findById(1L)).thenReturn(Optional.of(activo));
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(0L);
        OrgUnit unidad = new OrgUnit();
        unidad.setId(7L);
        unidad.setNombre("Unidad de prueba");
        when(unidades.findById(7L)).thenReturn(Optional.of(unidad));
        request.setTipoAsignacion("UNIDAD");
        request.setAsignadoUnidadId(7L);
        request.setEstado("fuera de servicio");
        var response = service.actualizar(1L, request);
        assertThat(response.codigo()).isEqualTo("INV-2025-012");
        assertThat(response.unidad()).isEqualTo("Unidad de prueba");
        assertThat(response.responsable()).isEmpty();
        assertThat(response.asignadoUnidadId()).isEqualTo(7L);
        assertThat(response.asignadoUsuarioId()).isNull();
        assertThat(response.estado()).isEqualTo("fuera de servicio");
        verify(repository, never()).bloquearGeneracionCodigo();
        verify(repository, never()).ultimoNumeroCodigo(anyString());
        verify(audit).saveAndFlush(argThat(log -> log.getDetalle().contains("unidad")
                && log.getDetalle().contains("asignadoUnidadId") && log.getDetalle().contains("estado")));
    }

    @Test void assignsAnExistingPersonAndClearsPreviousUnit() {
        guardar();
        var activo = activo();
        OrgUnit anterior = new OrgUnit();
        anterior.setId(7L);
        activo.setAsignadoUnidad(anterior);
        activo.setUnidad("Unidad de prueba");
        when(repository.findById(1L)).thenReturn(Optional.of(activo));
        Usuario persona = new Usuario();
        persona.setId(8L);
        persona.setNombre("usuario_prueba");
        persona.setApellido("Apellido ficticio");
        when(usuarios.findById(8L)).thenReturn(Optional.of(persona));
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(0L);
        request.setTipoAsignacion("PERSONA");
        request.setAsignadoUsuarioId(8L);
        var response = service.actualizar(1L, request);
        assertThat(response.tipoAsignacion()).isEqualTo("PERSONA");
        assertThat(response.asignadoUsuarioId()).isEqualTo(8L);
        assertThat(response.asignadoUnidadId()).isNull();
        assertThat(response.unidad()).isEmpty();
        assertThat(response.responsable()).isEqualTo("usuario_prueba Apellido ficticio");
        verify(audit).saveAndFlush(argThat(log -> log.getDetalle().contains("asignadoUsuarioId")));
    }

    @Test void rejectsConflictingAssignmentBeforeSaving() {
        var request = completar(new InventarioCreateRequest());
        request.setTipoAsignacion("PERSONA");
        request.setAsignadoUsuarioId(8L);
        request.setAsignadoUnidadId(7L);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, audit, usuarios, unidades);
    }

    @Test void rejectsFreeTextAssignments() {
        var request = completar(new InventarioCreateRequest());
        request.setResponsable("usuario_prueba");
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, audit);
    }

    @Test void rejectsMissingOrInactiveDestinations() {
        var request = completar(new InventarioCreateRequest());
        request.setTipoAsignacion("PERSONA");
        request.setAsignadoUsuarioId(8L);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(IllegalArgumentException.class);
        Usuario persona = new Usuario();
        persona.setId(8L);
        persona.setActivo(false);
        when(usuarios.findById(8L)).thenReturn(Optional.of(persona));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(IllegalArgumentException.class);
        request.setTipoAsignacion("UNIDAD");
        request.setAsignadoUsuarioId(null);
        request.setAsignadoUnidadId(7L);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(IllegalArgumentException.class);
        OrgUnit unidad = new OrgUnit();
        unidad.setId(7L);
        unidad.setActivo(false);
        when(unidades.findById(7L)).thenReturn(Optional.of(unidad));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, audit);
    }

    @Test void flagsLegacyTextWithoutGuessingAnIdentity() {
        var activo = activo();
        activo.setResponsable("usuario_prueba");
        when(repository.findAllByOrderByCodigoAsc()).thenReturn(java.util.List.of(activo));
        var response = service.listar().getFirst();
        assertThat(response.tipoAsignacion()).isEqualTo("PENDIENTE_REVISION");
        assertThat(response.responsable()).isEqualTo("usuario_prueba");
        assertThat(response.asignadoUsuarioId()).isNull();
        verifyNoInteractions(usuarios, unidades, audit);
    }

    @Test void changingBetweenPeopleWithTheSameNameIsAuditedById() {
        guardar();
        Usuario anterior = new Usuario();
        anterior.setId(8L);
        anterior.setNombre("usuario_prueba");
        Usuario siguiente = new Usuario();
        siguiente.setId(9L);
        siguiente.setNombre("usuario_prueba");
        var activo = activo();
        activo.setAsignadoUsuario(anterior);
        activo.setResponsable("usuario_prueba");
        when(repository.findById(1L)).thenReturn(Optional.of(activo));
        when(usuarios.findById(9L)).thenReturn(Optional.of(siguiente));
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(0L);
        request.setTipoAsignacion("PERSONA");
        request.setAsignadoUsuarioId(9L);
        service.actualizar(1L, request);
        verify(audit).saveAndFlush(argThat(log -> log.getDetalle().contains("asignadoUsuarioId: '8' -> '9'")));
    }

    @Test void preservesAnExistingInactiveAssigneeWithoutCreatingANewAssignment() {
        Usuario persona = new Usuario();
        persona.setId(8L);
        persona.setNombre("usuario_prueba");
        persona.setActivo(false);
        var activo = activo();
        activo.setAsignadoUsuario(persona);
        activo.setResponsable("usuario_prueba");
        when(repository.findById(1L)).thenReturn(Optional.of(activo));
        when(usuarios.findById(8L)).thenReturn(Optional.of(persona));
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(0L);
        request.setTipoAsignacion("PERSONA");
        request.setAsignadoUsuarioId(8L);
        var response = service.actualizar(1L, request);
        assertThat(response.asignadoUsuarioId()).isEqualTo(8L);
        verifyNoInteractions(audit);
    }

    @Test void doesNotAcceptAManualCodeOrChangingAnExistingCode() {
        var create = completar(new InventarioCreateRequest());
        create.setCodigo("INV-2025-012");
        assertThatThrownBy(() -> service.crear(create)).isInstanceOf(IllegalArgumentException.class);
        when(repository.findById(1L)).thenReturn(Optional.of(activo()));
        var update = completar(new InventarioUpdateRequest());
        update.setLockVersion(0L);
        update.setCodigo("INV-2025-013");
        assertThatThrownBy(() -> service.actualizar(1L, update)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void staleEditIsRejectedBeforeChangingAnything() {
        var activo = activo();
        when(repository.findById(1L)).thenReturn(Optional.of(activo));
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(1L);
        request.setNombre("Otro nombre de prueba");
        assertThatThrownBy(() -> service.actualizar(1L, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(activo.getNombre()).isEqualTo("Silla de prueba");
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void unchangedEditDoesNotCreateAnAuditMovement() {
        var activo = activo();
        when(repository.findById(1L)).thenReturn(Optional.of(activo));
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(0L);
        service.actualizar(1L, request);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void removingAnAssignmentIsAnAuditedChange() {
        guardar();
        var activo = activo();
        activo.setUnidad("Unidad de prueba");
        activo.setResponsable("usuario_prueba");
        when(repository.findById(1L)).thenReturn(Optional.of(activo));
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(0L);
        var response = service.actualizar(1L, request);
        assertThat(response.unidad()).isEmpty();
        assertThat(response.responsable()).isEmpty();
        verify(audit).saveAndFlush(any(AuditLog.class));
    }

    /** Comprueba el límite transaccional de Spring sin iniciar una base de datos. */
    private InventarioService transaccional(PlatformTransactionManager manager) {
        var proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(interceptor);
        return (InventarioService) proxy.getProxy();
    }

    @Test void failedCreationAuditRollsBackInsteadOfCommitting() {
        guardar();
        when(audit.saveAndFlush(any())).thenThrow(new IllegalStateException("Fallo ficticio de auditoría"));
        var manager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        var transactionalService = transaccional(manager);
        assertThatThrownBy(() -> transactionalService.crear(completar(new InventarioCreateRequest())))
                .isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test void failedEditAuditRollsBackInsteadOfCommitting() {
        guardar();
        when(repository.findById(1L)).thenReturn(Optional.of(activo()));
        when(audit.saveAndFlush(any())).thenThrow(new IllegalStateException("Fallo ficticio de auditoría"));
        var manager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        var request = completar(new InventarioUpdateRequest());
        request.setLockVersion(0L);
        request.setEstado("dañado");
        assertThatThrownBy(() -> transaccional(manager).actualizar(1L, request))
                .isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test void failedDeletionAuditRollsBackInsteadOfCommitting() {
        when(repository.findById(1L)).thenReturn(Optional.of(activo()));
        when(audit.saveAndFlush(any())).thenThrow(new IllegalStateException("Fallo ficticio de auditoría"));
        var manager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        assertThatThrownBy(() -> transaccional(manager).eliminar(1L)).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }
}
