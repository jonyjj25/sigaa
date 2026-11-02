package com.sigaa.service;

import com.sigaa.model.Nota;
import com.sigaa.model.NotaHistorial;
import com.sigaa.model.NotaPdf;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaPdfRepository;
import com.sigaa.repository.NotaRepository;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NotaDocumentoService {
    private final NotaRepository notaRepository;
    private final NotaPdfRepository pdfRepository;
    private final NotaPdfStorageService storage;
    private final NotaTrazabilidadService trazabilidad;

    public NotaDocumentoService(NotaRepository notaRepository, NotaPdfRepository pdfRepository,
                                NotaPdfStorageService storage, NotaTrazabilidadService trazabilidad) {
        this.notaRepository = notaRepository;
        this.pdfRepository = pdfRepository;
        this.storage = storage;
        this.trazabilidad = trazabilidad;
    }

    /** Agregar un PDF no modifica ningún otro dato funcional de la nota. */
    @Transactional
    public NotaPdf adjuntar(Long notaId, MultipartFile archivo, Usuario usuario) {
        Nota nota = notaRepository.findByIdForUpdate(notaId)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        if (!estadoAdmitePdf(nota) || !trazabilidad.firmaReceptorRegistrada(nota)) {
            throw new IllegalStateException("Solo puedes adjuntar el PDF después de registrar la firma del receptor.");
        }
        int versionAnterior = pdfRepository.maxVersion(notaId);
        NotaPdfStorageService.StoredPdf almacenado = storage.guardar(archivo);
        pdfRepository.desmarcarVigente(notaId);

        NotaPdf pdf = new NotaPdf();
        pdf.setNota(nota);
        pdf.setNumeroVersion(versionAnterior + 1);
        pdf.setNombreOriginal(almacenado.nombre());
        pdf.setRutaInterna(almacenado.ruta());
        pdf.setTipoMime(almacenado.tipoMime());
        pdf.setTamanoBytes(almacenado.tamanoBytes());
        pdf.setUsuario(usuario);
        pdf.setVigente(true);
        NotaPdf guardado = pdfRepository.save(pdf);

        // Puntero compatible para clientes y registros anteriores a V100.
        nota.setPdfNombre(almacenado.nombre());
        nota.setPdfRuta(almacenado.ruta());
        nota.setPdfTamanoBytes(almacenado.tamanoBytes());
        notaRepository.save(nota);

        String accion = versionAnterior == 0 ? "ADJUNTAR_PDF" : "REEMPLAZAR_PDF";
        String detalle = "PDF v" + guardado.getNumeroVersion() + " adjuntado: "
            + guardado.getNombreOriginal() + " (" + guardado.getTamanoBytes() + " bytes).";
        trazabilidad.historial(nota, usuario, accion, nota.getEstado(), nota.getEstado(), detalle);
        trazabilidad.audit(accion, notaId, detalle);
        return guardado;
    }

    public boolean puedeAdjuntar(Nota nota, List<NotaHistorial> historial) {
        return estadoAdmitePdf(nota) && trazabilidad.firmaReceptorRegistrada(historial);
    }

    private boolean estadoAdmitePdf(Nota nota) {
        return NotaService.FIRMADA.equals(nota.getEstado()) || NotaService.CERRADA.equals(nota.getEstado());
    }

    @Transactional(readOnly = true)
    public List<NotaPdf> listar(Long notaId) {
        if (!notaRepository.existsById(notaId)) throw new NoSuchElementException("La nota no existe.");
        return pdfRepository.findByNotaIdAndEliminadoAtIsNullOrderByNumeroVersionDesc(notaId);
    }

    @Transactional(readOnly = true)
    public NotaPdf vigente(Long notaId) {
        if (!notaRepository.existsById(notaId)) throw new NoSuchElementException("La nota no existe.");
        return pdfRepository.findFirstByNotaIdAndVigenteTrueAndEliminadoAtIsNullOrderByNumeroVersionDesc(notaId)
            .orElseThrow(() -> new NoSuchElementException("La nota todavía no tiene un PDF adjunto."));
    }

    @Transactional(readOnly = true)
    public NotaPdf obtener(Long notaId, Long pdfId) {
        return pdfRepository.findByIdAndNotaIdAndEliminadoAtIsNull(pdfId, notaId)
            .orElseThrow(() -> new NoSuchElementException("La versión del PDF no existe."));
    }

    @Transactional
    public void eliminar(Long notaId, Long pdfId, String motivo, Usuario usuario) {
        Nota nota = notaRepository.findByIdForUpdate(notaId)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        NotaPdf pdf = pdfRepository.findByIdAndNotaIdAndEliminadoAtIsNull(pdfId, notaId)
            .orElseThrow(() -> new NoSuchElementException("La versión del PDF no existe o ya fue eliminada."));
        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (motivoLimpio.length() < 5) throw new IllegalArgumentException("Debes indicar un motivo de eliminación válido.");

        boolean eraVigente = Boolean.TRUE.equals(pdf.getVigente());
        pdf.setVigente(false);
        pdf.setEliminadoPor(usuario);
        pdf.setEliminadoAt(LocalDateTime.now());
        pdf.setEliminacionMotivo(motivoLimpio);
        pdfRepository.save(pdf);

        if (eraVigente) {
            NotaPdf anterior = pdfRepository.findFirstByNotaIdAndEliminadoAtIsNullOrderByNumeroVersionDesc(notaId).orElse(null);
            if (anterior == null) {
                nota.setPdfNombre(null);
                nota.setPdfRuta(null);
                nota.setPdfTamanoBytes(null);
            } else {
                anterior.setVigente(true);
                pdfRepository.save(anterior);
                nota.setPdfNombre(anterior.getNombreOriginal());
                nota.setPdfRuta(anterior.getRutaInterna());
                nota.setPdfTamanoBytes(anterior.getTamanoBytes());
            }
            notaRepository.save(nota);
        }

        String detalle = "PDF v" + pdf.getNumeroVersion() + " eliminado: " + pdf.getNombreOriginal()
            + ". Motivo: " + motivoLimpio;
        trazabilidad.historial(nota, usuario, "ELIMINAR_PDF", nota.getEstado(), nota.getEstado(), detalle);
        trazabilidad.audit("ELIMINAR_PDF", notaId, detalle);
        storage.eliminarTrasConfirmar(pdf.getRutaInterna());
    }

    public Path archivo(NotaPdf pdf) { return storage.archivo(pdf); }
}
