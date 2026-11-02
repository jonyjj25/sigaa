package com.sigaa.service;

import com.sigaa.model.*;
import com.sigaa.repository.MemoNumeroRepository;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoNumeroService {
    public static final String DISPONIBLE = "DISPONIBLE", RESERVADO = "RESERVADO", OCUPADO = "OCUPADO";
    private static final int MINUTOS_RESERVA = 10, VENTANA_NUMEROS = 5;
    private final MemoNumeroRepository repository;
    private final MemoTrazabilidadService trazabilidad;

    public MemoNumeroService(MemoNumeroRepository repository, MemoTrazabilidadService trazabilidad) {
        this.repository = repository; this.trazabilidad = trazabilidad;
    }

    @Transactional public List<MemoNumero> listar() {
        repository.liberarReservasExpiradas(LocalDateTime.now());
        return repository.findAllByOrderByNumeroAsc();
    }

    @Transactional public MemoNumero reservar(Long id, Usuario usuario) {
        List<MemoNumero> inventario = repository.findAllForUpdate();
        MemoNumero numero = buscar(inventario, id);
        LocalDateTime ahora = LocalDateTime.now();
        inventario.forEach(item -> normalizarExpirada(item, ahora));
        if (OCUPADO.equals(numero.getEstado())) throw new IllegalStateException("Este número ya está ocupado.");
        if (RESERVADO.equals(numero.getEstado()) && !esPropietario(numero, usuario)) {
            throw new IllegalStateException("Este número acaba de ser reservado por otra persona.");
        }
        if (!esPropietario(numero, usuario)) exigirDentroDeVentana(inventario, numero);
        numero.setEstado(RESERVADO); numero.setReservadoPor(usuario); numero.setReservadoDesde(ahora);
        numero.setReservadoHasta(ahora.plusMinutes(MINUTOS_RESERVA));
        MemoNumero guardado = repository.save(numero);
        trazabilidad.audit("RESERVAR_NUMERO", null, "Se reservó temporalmente el número de memo " + numero.getNumero() + ".");
        return guardado;
    }

    @Transactional public void liberar(Long id, Usuario usuario, boolean administrador) {
        MemoNumero numero = repository.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("El número seleccionado no existe."));
        normalizarExpirada(numero, LocalDateTime.now());
        if (DISPONIBLE.equals(numero.getEstado())) return;
        if (OCUPADO.equals(numero.getEstado())) throw new IllegalStateException("Un número ocupado no puede liberarse.");
        if (!administrador && !esPropietario(numero, usuario)) throw new AccessDeniedException("La reserva pertenece a otro usuario.");
        limpiarReserva(numero); repository.save(numero);
        trazabilidad.audit("LIBERAR_NUMERO", null, "Se liberó la reserva del memo " + numero.getNumero() + ".");
    }

    @Transactional public MemoNumero ocuparReserva(Long id, Usuario usuario) {
        List<MemoNumero> inventario = repository.findAllForUpdate();
        MemoNumero numero = buscar(inventario, id);
        LocalDateTime ahora = LocalDateTime.now();
        if (!RESERVADO.equals(numero.getEstado()) || expirada(numero, ahora)) {
            normalizarExpirada(numero, ahora);
            throw new IllegalStateException("La reserva del número venció o ya no está disponible.");
        }
        if (!esPropietario(numero, usuario)) throw new AccessDeniedException("La reserva pertenece a otro usuario.");
        numero.setEstado(OCUPADO); numero.setReservadoPor(null); numero.setReservadoDesde(null); numero.setReservadoHasta(null);
        return repository.save(numero);
    }

    private MemoNumero buscar(List<MemoNumero> items, Long id) {
        return items.stream().filter(item -> item.getId().equals(id)).findFirst()
            .orElseThrow(() -> new NoSuchElementException("El número seleccionado no existe."));
    }

    private void exigirDentroDeVentana(List<MemoNumero> inventario, MemoNumero seleccionado) {
        Comparator<MemoNumero> orden = Comparator.comparing((MemoNumero item) -> valorNumerico(item.getNumero()))
            .thenComparing(MemoNumero::getNumero, String.CASE_INSENSITIVE_ORDER).thenComparing(MemoNumero::getId);
        MemoNumero actual = inventario.stream().filter(item -> OCUPADO.equals(item.getEstado())).max(orden).orElse(null);
        List<MemoNumero> disponibles = inventario.stream().filter(item -> DISPONIBLE.equals(item.getEstado()))
            .filter(item -> actual == null || orden.compare(item, actual) > 0).sorted(orden).limit(VENTANA_NUMEROS).toList();
        if (disponibles.isEmpty()) throw new IllegalStateException("No hay números disponibles para registrar el memo.");
        if (disponibles.stream().noneMatch(item -> item.getId().equals(seleccionado.getId()))) {
            throw new IllegalStateException("Selecciona uno de los primeros cinco números disponibles.");
        }
    }

    private BigInteger valorNumerico(String value) { String digits = value == null ? "" : value.replaceAll("\\D", ""); return digits.isEmpty() ? BigInteger.ZERO : new BigInteger(digits); }
    private boolean expirada(MemoNumero numero, LocalDateTime now) { return numero.getReservadoHasta() == null || !numero.getReservadoHasta().isAfter(now); }
    private boolean esPropietario(MemoNumero numero, Usuario usuario) { return numero.getReservadoPor() != null && numero.getReservadoPor().getId().equals(usuario.getId()); }
    private void normalizarExpirada(MemoNumero numero, LocalDateTime now) { if (RESERVADO.equals(numero.getEstado()) && expirada(numero, now)) { limpiarReserva(numero); repository.save(numero); } }
    private void limpiarReserva(MemoNumero numero) { numero.setEstado(DISPONIBLE); numero.setReservadoPor(null); numero.setReservadoDesde(null); numero.setReservadoHasta(null); }
}
