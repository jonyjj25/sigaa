package com.sigaa.service;

import com.sigaa.dto.NotaRelacionRequest;
import com.sigaa.model.Nota;
import com.sigaa.model.NotaNumero;
import com.sigaa.model.NotaRelacion;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaRelacionRepository;
import com.sigaa.repository.NotaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotaRelacionServiceTest {
    private NotaRepository notas;
    private NotaRelacionRepository relaciones;
    private NotaRelacionService service;
    private Nota uno;
    private Nota dos;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        notas = mock(NotaRepository.class); relaciones = mock(NotaRelacionRepository.class);
        service = new NotaRelacionService(notas, relaciones, mock(NotaTrazabilidadService.class));
        uno = nota(1L, "N-1"); dos = nota(2L, "N-2"); usuario = new Usuario(); usuario.setId(7L);
        when(notas.findById(1L)).thenReturn(Optional.of(uno)); when(notas.findById(2L)).thenReturn(Optional.of(dos));
        when(relaciones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rechazaAutorrelacion() {
        assertThatThrownBy(() -> service.crear(1L, request(1L, "RELACIONADA_CON"), usuario))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("consigo misma");
    }

    @Test
    void relacionSimetricaSeCanonicalizaYEvitaDuplicadoInverso() {
        NotaRelacion creada = service.crear(2L, request(1L, "RELACIONADA_CON"), usuario);
        assertThat(creada.getNotaOrigen()).isSameAs(uno);
        assertThat(creada.getNotaDestino()).isSameAs(dos);

        when(relaciones.existsByNotaOrigenIdAndNotaDestinoIdAndTipo(1L, 2L, "RELACIONADA_CON")).thenReturn(true);
        assertThatThrownBy(() -> service.crear(1L, request(2L, "RELACIONADA_CON"), usuario))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("misma relación");
    }

    @Test
    void eliminarRelacionNoEliminaLasNotas() {
        NotaRelacion relacion = new NotaRelacion(); relacion.setId(8L); relacion.setNotaOrigen(uno); relacion.setNotaDestino(dos); relacion.setTipo("RESPONDE_A");
        when(relaciones.findByIdForNota(8L, 1L)).thenReturn(Optional.of(relacion));
        service.eliminar(1L, 8L, usuario);
        verify(relaciones).delete(relacion);
        verify(notas, never()).delete(any());
    }

    private Nota nota(Long id, String numero) {
        Nota nota = new Nota(); nota.setId(id); nota.setEstado("EN_SEGUIMIENTO");
        NotaNumero consecutivo = new NotaNumero(); consecutivo.setId(id); consecutivo.setNumero(numero); nota.setNumero(consecutivo);
        return nota;
    }

    private NotaRelacionRequest request(Long id, String tipo) {
        NotaRelacionRequest request = new NotaRelacionRequest(); request.setNotaRelacionadaId(id); request.setTipo(tipo); return request;
    }
}
