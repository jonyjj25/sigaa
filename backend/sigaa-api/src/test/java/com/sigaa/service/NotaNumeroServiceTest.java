package com.sigaa.service;

import com.sigaa.model.NotaNumero;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaNumeroRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotaNumeroServiceTest {
    private NotaNumeroRepository repository;
    private NotaNumeroService service;
    private NotaNumero numero;
    private Usuario ana;
    private Usuario luis;

    @BeforeEach
    void setUp() {
        repository = mock(NotaNumeroRepository.class);
        service = new NotaNumeroService(repository, mock(NotaTrazabilidadService.class));
        numero = new NotaNumero(); numero.setId(10L); numero.setNumero("N-010"); numero.setEstado("DISPONIBLE");
        numero.setConsecutivo(10);
        ana = usuario(1L); luis = usuario(2L);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(numero));
        when(repository.findAllForUpdate()).thenReturn(List.of(numero));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void soloElPrimerUsuarioPuedeReservarElNumero() {
        service.reservar(10L, ana);

        assertThat(numero.getEstado()).isEqualTo("RESERVADO");
        assertThat(numero.getReservadoPor()).isSameAs(ana);
        assertThatThrownBy(() -> service.reservar(10L, luis))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("otra persona");
    }

    @Test
    void unaReservaVencidaPuedeSerTomadaPorOtroUsuario() {
        numero.setEstado("RESERVADO"); numero.setReservadoPor(ana);
        numero.setReservadoDesde(LocalDateTime.now().minusMinutes(20));
        numero.setReservadoHasta(LocalDateTime.now().minusMinutes(5));

        service.reservar(10L, luis);

        assertThat(numero.getReservadoPor()).isSameAs(luis);
        assertThat(numero.getReservadoHasta()).isAfter(LocalDateTime.now().plusMinutes(9));
    }

    @Test
    void soloElPropietarioPuedeRenovarOLiberar() {
        service.reservar(10L, ana);
        LocalDateTime vencimiento = numero.getReservadoHasta();

        assertThatThrownBy(() -> service.renovar(10L, luis)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.liberar(10L, luis, false)).isInstanceOf(AccessDeniedException.class);
        service.renovar(10L, ana);
        assertThat(numero.getReservadoHasta()).isAfterOrEqualTo(vencimiento);
        service.liberar(10L, ana, false);
        assertThat(numero.getEstado()).isEqualTo("DISPONIBLE");
    }

    @Test
    void convierteLaReservaPropiaEnOcupacionAtomica() {
        service.reservar(10L, ana);
        service.ocuparReserva(10L, ana);

        assertThat(numero.getEstado()).isEqualTo("OCUPADO");
        assertThat(numero.getReservadoPor()).isNull();
        assertThatThrownBy(() -> service.ocuparReserva(10L, ana)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void limpiezaProgramadaLiberaReservasExpiradas() {
        when(repository.liberarReservasExpiradas(any())).thenReturn(3);
        assertThat(service.liberarExpiradas()).isEqualTo(3);
    }

    @Test
    void reservaDeOtroAnioNoSePuedeUsarEnUnaNuevaNota() {
        numero.setAnio(java.time.LocalDate.now(java.time.ZoneId.of("America/Panama")).getYear() - 1);
        assertThatThrownBy(() -> service.reservar(10L, ana)).hasMessageContaining("año actual");
        numero.setEstado("RESERVADO"); numero.setReservadoPor(ana);
        numero.setReservadoHasta(LocalDateTime.now().plusMinutes(5));
        assertThatThrownBy(() -> service.ocuparReserva(10L, ana)).hasMessageContaining("año actual");
        assertThat(numero.getEstado()).isEqualTo("RESERVADO");
    }

    @Test
    void identificadoresHistoricosAmbiguosNoSeReservanParaNuevasNotas() {
        numero.setConsecutivo(null);
        assertThatThrownBy(() -> service.reservar(10L, ana)).hasMessageContaining("identificador histórico");
        assertThat(numero.getEstado()).isEqualTo("DISPONIBLE");
        verify(repository, never()).save(any());
    }

    @Test
    void cargaNormalizaNumerosRechazaDuplicadosYNoMezclaAnios() {
        int year = numero.getAnio();
        assertThat(service.cargar(List.of("015", "OAT-016-" + year))).isEqualTo(2);
        org.mockito.ArgumentCaptor<Iterable<NotaNumero>> captured = org.mockito.ArgumentCaptor.captor();
        verify(repository).saveAll(captured.capture());
        assertThat(captured.getValue()).extracting(NotaNumero::getNumero)
            .containsExactly("OAT-015-" + year, "OAT-016-" + year);
        assertThatThrownBy(() -> service.cargar(List.of("017", "N-017"))).hasMessageContaining("repetido");
        assertThatThrownBy(() -> service.cargar(List.of("OAT-017-" + (year + 1)))).hasMessageContaining("año actual");
        assertThatThrownBy(() -> service.cargar(List.of("000"))).hasMessageContaining("001");
        assertThatThrownBy(() -> service.cargar(List.of("1000"))).hasMessageContaining("999");
    }

    @Test
    void soloPermiteElPrimerDisponibleDeLaSecuenciaAunqueHayaCincoDisponibles() {
        NotaNumero anterior = numero(9L, "N-009", "DISPONIBLE");
        NotaNumero actual = numero(10L, "N-010", "OCUPADO");
        NotaNumero siguiente = numero(11L, "N-011", "DISPONIBLE");
        NotaNumero doce = numero(12L, "N-012", "DISPONIBLE");
        NotaNumero trece = numero(13L, "N-013", "DISPONIBLE");
        NotaNumero catorce = numero(14L, "N-014", "DISPONIBLE");
        NotaNumero quince = numero(15L, "N-015", "DISPONIBLE");
        NotaNumero dieciseis = numero(16L, "N-016", "DISPONIBLE");

        when(repository.findAllForUpdate()).thenReturn(List.of(anterior, actual, siguiente, doce, trece, catorce, quince, dieciseis));

        assertThatThrownBy(() -> service.reservar(9L, ana))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("primer número disponible: N-011");
        assertThatThrownBy(() -> service.reservar(16L, ana))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("primer número disponible");
        assertThatThrownBy(() -> service.reservar(12L, ana))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("primer número disponible");
        assertThat(service.reservar(11L, ana).getEstado()).isEqualTo("RESERVADO");
        assertThat(service.reservar(12L, luis).getEstado()).isEqualTo("RESERVADO");
    }

    @Test
    void conservaReservaPropiaVigenteAunqueSeLibereUnNumeroAnterior() {
        NotaNumero anterior = numero(9L, "N-009", "DISPONIBLE");
        numero.setEstado("RESERVADO"); numero.setReservadoPor(ana);
        numero.setReservadoDesde(LocalDateTime.now().minusMinutes(1));
        numero.setReservadoHasta(LocalDateTime.now().plusMinutes(5));
        when(repository.findAllForUpdate()).thenReturn(List.of(anterior, numero));

        assertThat(service.reservar(10L, ana).getReservadoPor()).isSameAs(ana);
        assertThat(service.renovar(10L, ana).getEstado()).isEqualTo("RESERVADO");
        assertThat(service.ocuparReserva(10L, ana).getEstado()).isEqualTo("OCUPADO");
        assertThat(anterior.getEstado()).isEqualTo("DISPONIBLE");
    }

    @Test
    void reservaPropiaVencidaYaNoPermiteSaltarElPrimerDisponible() {
        NotaNumero anterior = numero(9L, "N-009", "DISPONIBLE");
        numero.setEstado("RESERVADO"); numero.setReservadoPor(ana);
        numero.setReservadoDesde(LocalDateTime.now().minusMinutes(20));
        numero.setReservadoHasta(LocalDateTime.now().minusMinutes(1));
        when(repository.findAllForUpdate()).thenReturn(List.of(anterior, numero));

        assertThatThrownBy(() -> service.reservar(10L, ana)).hasMessageContaining("N-009");
        assertThat(numero.getEstado()).isEqualTo("DISPONIBLE");
        assertThat(numero.getReservadoPor()).isNull();
    }

    @Test
    void numeroVencidoAnteriorDebeRecuperarSuPosicionAntesDeElegirElSiguiente() {
        NotaNumero anterior = numero(9L, "N-009", "RESERVADO");
        anterior.setReservadoPor(luis); anterior.setReservadoDesde(LocalDateTime.now().minusMinutes(20));
        anterior.setReservadoHasta(LocalDateTime.now().minusMinutes(1));
        when(repository.findAllForUpdate()).thenReturn(List.of(numero, anterior));

        assertThatThrownBy(() -> service.reservar(10L, ana)).hasMessageContaining("N-009");
        assertThat(service.reservar(9L, ana).getReservadoPor()).isSameAs(ana);
    }

    @Test
    void disponiblesDeOtrosAniosNoAlteranElPrimeroDelAnioActual() {
        NotaNumero antiguo = numero(1L, "N-001", "DISPONIBLE"); antiguo.setAnio(numero.getAnio() - 1);
        when(repository.findAllForUpdate()).thenReturn(List.of(antiguo, numero));
        assertThat(service.reservar(10L, ana).getEstado()).isEqualTo("RESERVADO");
    }

    private Usuario usuario(Long id) { Usuario usuario = new Usuario(); usuario.setId(id); return usuario; }
    private NotaNumero numero(Long id, String valor, String estado) {
        NotaNumero item = new NotaNumero(); item.setId(id); item.setNumero(valor); item.setEstado(estado);
        item.setConsecutivo(Integer.parseInt(valor.substring(2))); return item;
    }
}
