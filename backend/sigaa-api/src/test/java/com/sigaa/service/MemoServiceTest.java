package com.sigaa.service;

import com.sigaa.dto.MemoCreateRequest;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoServiceTest {
    private MemoRepository repository;
    private UsuarioRepository usuarios;
    private MemoNumeroService numeros;
    private MemoTrazabilidadService trazabilidad;
    private MemoService service;
    private Usuario actual;
    private Memo memo;

    @BeforeEach void setUp() {
        repository = mock(MemoRepository.class); usuarios = mock(UsuarioRepository.class);
        numeros = mock(MemoNumeroService.class); trazabilidad = mock(MemoTrazabilidadService.class);
        service = new MemoService(repository, usuarios, numeros, trazabilidad);
        actual = new Usuario(); actual.setId(3L); actual.setActivo(true); actual.setBloqueado(false);
        MemoNumero numero = new MemoNumero(); numero.setId(7L); numero.setNumero("MEMO-2026-001"); numero.setEstado("OCUPADO");
        memo = new Memo(); memo.setId(9L); memo.setNumero(numero); memo.setAsunto("Asunto"); memo.setDestino("Unidad"); memo.setCreadoPor(actual); memo.setSeguimientoUsuario(actual); memo.setEstado("EN_SEGUIMIENTO");
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(memo));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void creaMemoConReservaYRegistraTrazabilidad() {
        MemoCreateRequest request = new MemoCreateRequest(); request.setNumeroId(7L); request.setAsunto("  Asunto institucional "); request.setDestino(" Dirección "); request.setSeguimientoUsuarioId(3L); request.setDescripcion("Gestión inicial");
        when(numeros.ocuparReserva(7L, actual)).thenReturn(memo.getNumero());
        when(usuarios.findById(3L)).thenReturn(Optional.of(actual));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> { Memo value = invocation.getArgument(0); value.setId(9L); return value; });

        Memo creado = service.crear(request, actual);

        assertThat(creado.getAsunto()).isEqualTo("Asunto institucional");
        assertThat(creado.getEstado()).isEqualTo("EN_SEGUIMIENTO");
        verify(trazabilidad).historial(eq(creado), eq(actual), eq("CREAR"), isNull(), eq("EN_SEGUIMIENTO"), contains("MEMO-2026-001"));
        verify(trazabilidad).historial(eq(creado), eq(actual), eq("SEGUIMIENTO_INICIAL"), isNull(), eq("EN_SEGUIMIENTO"), eq("Gestión inicial"));
    }

    @Test void agregaSeguimientoSinSobrescribirLaDescripcionInicial() {
        memo.setDescripcion("Información original");
        service.seguimiento(9L, "Se recibió respuesta de la unidad", actual);
        assertThat(memo.getDescripcion()).isEqualTo("Información original");
        assertThat(memo.getUpdatedAt()).isNotNull();
        verify(trazabilidad).historial(eq(memo), eq(actual), eq("SEGUIMIENTO"), eq("EN_SEGUIMIENTO"), eq("EN_SEGUIMIENTO"), contains("respuesta"));
    }

    @Test void completaMemoEnSeguimiento() {
        Memo result = service.completar(9L, actual);
        assertThat(result.getEstado()).isEqualTo("COMPLETADO");
        assertThat(result.getCompletadoPor()).isSameAs(actual);
        assertThat(result.getCompletadoAt()).isNotNull();
    }

    @Test void anulacionExigeMotivoValido() {
        assertThatThrownBy(() -> service.anular(9L, " ", actual)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        verify(repository, never()).save(any());
    }
}
