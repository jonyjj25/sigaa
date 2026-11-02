package com.sigaa.service;

import com.sigaa.model.*;
import com.sigaa.repository.MemoNumeroRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoNumeroServiceTest {
    @Test void soloPermiteReservarUnoDeLosPrimerosCincoDisponibles() {
        MemoNumeroRepository repository = mock(MemoNumeroRepository.class);
        MemoTrazabilidadService trazabilidad = mock(MemoTrazabilidadService.class);
        MemoNumeroService service = new MemoNumeroService(repository, trazabilidad);
        Usuario usuario = new Usuario(); usuario.setId(2L);
        List<MemoNumero> inventario = new ArrayList<>();
        for (long i = 1; i <= 6; i++) { MemoNumero item = new MemoNumero(); item.setId(i); item.setNumero(String.valueOf(100 + i)); item.setEstado("DISPONIBLE"); inventario.add(item); }
        when(repository.findAllForUpdate()).thenReturn(inventario);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.reservar(6L, usuario)).isInstanceOf(IllegalStateException.class).hasMessageContaining("primeros cinco");
        MemoNumero reservado = service.reservar(1L, usuario);
        assertThat(reservado.getEstado()).isEqualTo("RESERVADO");
        assertThat(reservado.getReservadoHasta()).isAfter(reservado.getReservadoDesde());
    }
}
