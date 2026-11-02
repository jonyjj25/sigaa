package com.sigaa.service;

import com.sigaa.dto.ModuloConfiguracionRequest;
import com.sigaa.dto.ModuloRequest;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Modulo;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.ModuloRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuloServiceTest {

    @Mock
    private ModuloRepository moduloRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private ModuloService service;

    @BeforeEach
    void setUp() {
        service = new ModuloService(moduloRepository, auditLogRepository);
    }

    @Test
    void presentationUpdateDoesNotReactivateAnInactiveModule() {
        Modulo module = module(1L, "Documentos", "documentos", "/documentos", 1, false);
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(module));
        when(moduloRepository.save(module)).thenReturn(module);
        ModuloRequest request = new ModuloRequest();
        request.setNombre("Gestión documental");
        request.setColor("#2563eb");

        var response = service.actualizar(1L, request);

        assertThat(response.getNombre()).isEqualTo("Gestión documental");
        assertThat(response.getActivo()).isFalse();
    }

    @Test
    void installedRouteCannotBeChangedFromConfiguration() {
        Modulo module = module(1L, "Documentos", "documentos", "/documentos", 1, true);
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(module));
        ModuloRequest request = new ModuloRequest();
        request.setRuta("/otra-ruta");

        assertThatThrownBy(() -> service.actualizar(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruta");
        verify(moduloRepository, never()).save(any(Modulo.class));
    }

    @Test
    void retiredSolicitudesCannotBeRegisteredAgainThroughAnyTechnicalIdentifier() {
        for (String identifier : List.of("codigo", "ruta", "ruta-anidada", "api", "api-anidada")) {
            ModuloRequest request = new ModuloRequest();
            request.setNombre("Módulo de prueba");
            request.setCodigo("prueba");
            request.setRuta("/prueba");
            switch (identifier) {
                case "codigo" -> request.setCodigo("solicitudes");
                case "ruta" -> request.setRuta("/solicitudes/");
                case "ruta-anidada" -> request.setRuta("/solicitudes/pendientes");
                case "api" -> request.setRutaApi("/api/solicitudes");
                case "api-anidada" -> request.setRutaApi("/api/solicitudes/tipos");
                default -> throw new AssertionError(identifier);
            }
            assertThatThrownBy(() -> service.crear(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("retirado");
        }
        verify(moduloRepository, never()).save(any(Modulo.class));
    }

    @Test
    void activeModulesCanStillBeRegistered() {
        ModuloRequest request = new ModuloRequest();
        request.setNombre("Documentos");
        request.setCodigo("documentos");
        request.setRuta("/documentos");
        when(moduloRepository.save(any(Modulo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.crear(request).getCodigo()).isEqualTo("documentos");
        verify(moduloRepository).save(any(Modulo.class));
    }

    @Test
    void deleteArchivesTheModuleWithoutRemovingItsRegistryRow() {
        Modulo module = module(1L, "Documentos", "documentos", "/documentos", 1, true);
        module.setVisibleEnMenu(true);
        module.setVisibleEnDashboard(true);
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(module));
        when(moduloRepository.save(module)).thenReturn(module);

        service.eliminar(1L);

        assertThat(module.getActivo()).isFalse();
        assertThat(module.getVisibleEnMenu()).isFalse();
        assertThat(module.getVisibleEnDashboard()).isFalse();
        verify(moduloRepository, never()).delete(any(Modulo.class));
    }

    @Test
    void fullConfigurationIsValidatedSavedAndAuditedAsOneOperation() {
        Modulo first = module(1L, "Panel", "panel", "/dashboard", 1, true);
        Modulo second = module(2L, "Documentos", "documentos", "/documentos", 2, true);
        when(moduloRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(first, second));
        when(moduloRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var firstRequest = configuration(1L, 2, false);
        var secondRequest = configuration(2L, 1, true);
        var result = service.guardarConfiguracion(List.of(firstRequest, secondRequest));

        assertThat(first.getOrden()).isEqualTo(2);
        assertThat(first.getActivo()).isFalse();
        assertThat(second.getOrden()).isEqualTo(1);
        assertThat(result).hasSize(2);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAccion()).isEqualTo("CONFIGURAR");
        assertThat(audit.getValue().getDetalle()).contains("Antes:", "Después:");
    }

    @Test
    void duplicateOrIncompleteOrderIsRejectedBeforeWriting() {
        Modulo first = module(1L, "Panel", "panel", "/dashboard", 1, true);
        Modulo second = module(2L, "Documentos", "documentos", "/documentos", 2, true);
        when(moduloRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.guardarConfiguracion(List.of(
                configuration(1L, 1, true),
                configuration(2L, 1, true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orden");
        verify(moduloRepository, never()).saveAll(any());
    }

    private Modulo module(Long id, String nombre, String codigo, String ruta, int orden, boolean activo) {
        Modulo module = new Modulo();
        module.setId(id);
        module.setNombre(nombre);
        module.setCodigo(codigo);
        module.setRuta(ruta);
        module.setOrden(orden);
        module.setActivo(activo);
        module.setVisibleEnMenu(true);
        module.setVisibleEnDashboard(true);
        return module;
    }

    private ModuloConfiguracionRequest configuration(Long id, int orden, boolean activo) {
        ModuloConfiguracionRequest request = new ModuloConfiguracionRequest();
        request.setId(id);
        request.setOrden(orden);
        request.setActivo(activo);
        return request;
    }
}
