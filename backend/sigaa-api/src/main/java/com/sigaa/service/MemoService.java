package com.sigaa.service;

import com.sigaa.dto.MemoCreateRequest;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoService {
    public static final String EN_SEGUIMIENTO = "EN_SEGUIMIENTO", COMPLETADO = "COMPLETADO", ANULADO = "ANULADO";
    private final MemoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final MemoNumeroService numeroService;
    private final MemoTrazabilidadService trazabilidad;

    public MemoService(MemoRepository repository, UsuarioRepository usuarioRepository, MemoNumeroService numeroService, MemoTrazabilidadService trazabilidad) {
        this.repository = repository; this.usuarioRepository = usuarioRepository; this.numeroService = numeroService; this.trazabilidad = trazabilidad;
    }

    @Transactional(readOnly = true) public List<Memo> listar() { return repository.findAllByOrderByUpdatedAtDesc(); }
    @Transactional(readOnly = true) public Memo obtener(Long id) { return repository.findById(id).orElseThrow(() -> new NoSuchElementException("El memo no existe.")); }

    @Transactional public Memo crear(MemoCreateRequest request, Usuario creador) {
        MemoNumero numero = numeroService.ocuparReserva(request.getNumeroId(), creador);
        Usuario responsable = usuarioActivo(request.getSeguimientoUsuarioId());
        Memo memo = new Memo(); memo.setNumero(numero); memo.setAsunto(limpiar(request.getAsunto()));
        memo.setDestino(limpiar(request.getDestino())); memo.setDescripcion(descripcion(request.getDescripcion()));
        memo.setCreadoPor(creador); memo.setSeguimientoUsuario(responsable); memo.setEstado(EN_SEGUIMIENTO);
        Memo guardado = repository.saveAndFlush(memo);
        trazabilidad.historial(guardado, creador, "CREAR", null, EN_SEGUIMIENTO, "Se registró el memo con el número " + numero.getNumero() + ".");
        if (!guardado.getDescripcion().isBlank()) trazabilidad.historial(guardado, creador, "SEGUIMIENTO_INICIAL", null, EN_SEGUIMIENTO, guardado.getDescripcion());
        trazabilidad.audit("CREAR", guardado.getId(), "Memo " + numero.getNumero() + " creado.");
        return guardado;
    }

    @Transactional public Memo seguimiento(Long id, String detalle, Usuario usuario) {
        Memo memo = bloquearActivo(id);
        String limpio = descripcion(detalle);
        if (limpio.length() < 3) throw new IllegalArgumentException("Debes escribir una actualización de seguimiento válida.");
        memo.setUpdatedAt(LocalDateTime.now());
        repository.save(memo);
        trazabilidad.historial(memo, usuario, "SEGUIMIENTO", EN_SEGUIMIENTO, EN_SEGUIMIENTO, limpio);
        trazabilidad.audit("SEGUIMIENTO", id, "Se agregó seguimiento al memo " + memo.getNumero().getNumero() + ".");
        return memo;
    }

    @Transactional public Memo completar(Long id, Usuario usuario) {
        Memo memo = bloquearActivo(id); memo.setEstado(COMPLETADO); memo.setCompletadoPor(usuario); memo.setCompletadoAt(LocalDateTime.now());
        Memo guardado = repository.save(memo);
        trazabilidad.historial(guardado, usuario, "COMPLETAR", EN_SEGUIMIENTO, COMPLETADO, "El seguimiento del memo fue completado.");
        trazabilidad.audit("COMPLETAR", id, "Memo " + guardado.getNumero().getNumero() + " completado.");
        return guardado;
    }

    @Transactional public Memo anular(Long id, String motivo, Usuario usuario) {
        Memo memo = repository.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("El memo no existe."));
        if (ANULADO.equals(memo.getEstado())) throw new IllegalStateException("El memo ya se encuentra anulado.");
        String limpio = descripcion(motivo); if (limpio.length() < 5) throw new IllegalArgumentException("Debes indicar un motivo de anulación válido.");
        String anterior = memo.getEstado(); memo.setEstado(ANULADO); memo.setAnuladoPor(usuario); memo.setAnuladoAt(LocalDateTime.now()); memo.setAnulacionMotivo(limpio);
        Memo guardado = repository.save(memo);
        trazabilidad.historial(guardado, usuario, "ANULAR", anterior, ANULADO, "Motivo de anulación: " + limpio);
        trazabilidad.audit("ANULAR", id, "Memo " + guardado.getNumero().getNumero() + " anulado. Motivo: " + limpio);
        return guardado;
    }

    private Memo bloquearActivo(Long id) {
        Memo memo = repository.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("El memo no existe."));
        if (!EN_SEGUIMIENTO.equals(memo.getEstado())) throw new IllegalStateException("Solo los memos en seguimiento pueden modificarse.");
        return memo;
    }
    private Usuario usuarioActivo(Long id) { return usuarioRepository.findById(id).filter(u -> Boolean.TRUE.equals(u.getActivo()) && !Boolean.TRUE.equals(u.getBloqueado())).orElseThrow(() -> new IllegalArgumentException("La persona responsable no existe o no está activa.")); }
    private String limpiar(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private String descripcion(String value) { return value == null ? "" : value.trim(); }
}
