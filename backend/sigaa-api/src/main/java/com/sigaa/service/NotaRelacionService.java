package com.sigaa.service;

import com.sigaa.dto.NotaRelacionRequest;
import com.sigaa.model.Nota;
import com.sigaa.model.NotaRelacion;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaRelacionRepository;
import com.sigaa.repository.NotaRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotaRelacionService {
    private static final String RELACIONADA = "RELACIONADA_CON";
    private static final Set<String> TIPOS = Set.of(RELACIONADA, "RESPONDE_A", "ANTECEDENTE_DE");

    private final NotaRepository notaRepository;
    private final NotaRelacionRepository relacionRepository;
    private final NotaTrazabilidadService trazabilidad;

    public NotaRelacionService(NotaRepository notaRepository, NotaRelacionRepository relacionRepository,
                               NotaTrazabilidadService trazabilidad) {
        this.notaRepository = notaRepository;
        this.relacionRepository = relacionRepository;
        this.trazabilidad = trazabilidad;
    }

    @Transactional
    public NotaRelacion crear(Long notaId, NotaRelacionRequest request, Usuario usuario) {
        if (request == null || request.getNotaRelacionadaId() == null) {
            throw new IllegalArgumentException("Debes seleccionar la nota relacionada.");
        }
        String tipo = request.getTipo() == null ? "" : request.getTipo().trim().toUpperCase();
        if (!TIPOS.contains(tipo)) throw new IllegalArgumentException("El tipo de relación no es válido.");
        if (notaId.equals(request.getNotaRelacionadaId())) {
            throw new IllegalArgumentException("Una nota no puede relacionarse consigo misma.");
        }
        Nota principal = notaRepository.findById(notaId)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        Nota relacionada = notaRepository.findById(request.getNotaRelacionadaId())
            .orElseThrow(() -> new NoSuchElementException("La nota relacionada no existe."));

        Nota origen = principal;
        Nota destino = relacionada;
        if (RELACIONADA.equals(tipo) && origen.getId() > destino.getId()) {
            origen = relacionada;
            destino = principal;
        }
        if (relacionRepository.existsByNotaOrigenIdAndNotaDestinoIdAndTipo(origen.getId(), destino.getId(), tipo)) {
            throw new IllegalStateException("Estas notas ya tienen la misma relación.");
        }

        NotaRelacion relacion = new NotaRelacion();
        relacion.setNotaOrigen(origen);
        relacion.setNotaDestino(destino);
        relacion.setTipo(tipo);
        relacion.setCreadoPor(usuario);
        NotaRelacion guardada = relacionRepository.save(relacion);

        String detallePrincipal = "Se enlazó con la nota " + relacionada.getNumero().getNumero() + " (" + tipo + ").";
        String detalleRelacionada = "Se enlazó con la nota " + principal.getNumero().getNumero() + " (" + tipo + ").";
        trazabilidad.historial(principal, usuario, "RELACIONAR_NOTA", principal.getEstado(), principal.getEstado(), detallePrincipal);
        trazabilidad.historial(relacionada, usuario, "RELACIONAR_NOTA", relacionada.getEstado(), relacionada.getEstado(), detalleRelacionada);
        trazabilidad.audit("RELACIONAR_NOTA", principal.getId(), detallePrincipal);
        return guardada;
    }

    @Transactional
    public void eliminar(Long notaId, Long relacionId, Usuario usuario) {
        NotaRelacion relacion = relacionRepository.findByIdForNota(relacionId, notaId)
            .orElseThrow(() -> new NoSuchElementException("La relación no existe."));
        Nota origen = relacion.getNotaOrigen();
        Nota destino = relacion.getNotaDestino();
        relacionRepository.delete(relacion);
        String detalleOrigen = "Se eliminó el enlace con la nota " + destino.getNumero().getNumero()
            + " (" + relacion.getTipo() + ").";
        String detalleDestino = "Se eliminó el enlace con la nota " + origen.getNumero().getNumero()
            + " (" + relacion.getTipo() + ").";
        trazabilidad.historial(origen, usuario, "ELIMINAR_RELACION", origen.getEstado(), origen.getEstado(), detalleOrigen);
        trazabilidad.historial(destino, usuario, "ELIMINAR_RELACION", destino.getEstado(), destino.getEstado(), detalleDestino);
        trazabilidad.audit("ELIMINAR_RELACION", notaId, "Se eliminó la relación " + relacionId + ".");
    }

    @Transactional(readOnly = true)
    public List<NotaRelacion> listar(Long notaId) {
        if (!notaRepository.existsById(notaId)) throw new NoSuchElementException("La nota no existe.");
        return relacionRepository.findAllForNota(notaId);
    }
}
