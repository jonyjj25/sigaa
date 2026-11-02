package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.dto.DocumentoSolicitudAprobacionRequest;
import com.sigaa.dto.DocumentoResolucionAprobacionRequest;
import com.sigaa.dto.DocumentoCompartirRequest;
import com.sigaa.repository.*;
import com.sigaa.util.UploadStorage;
import com.sigaa.util.FileContentValidator;
import com.sigaa.service.DocumentoAccessService;
import com.sigaa.service.InternalNotificationService;
import com.sigaa.service.TareaAccessService;
import jakarta.validation.Valid;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final DocumentoRepository documentoRepository;
    private final CarpetaRepository carpetaRepository;
    private final CarpetaUsuarioRepository carpetaUsuarioRepository;
    private final DocumentoVersionRepository versionRepository;
    private final AuditLogRepository auditLogRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final DocumentoSolicitudAprobacionRepository solicitudAprobacionRepository;
    private final DocumentoUsuarioAccesoRepository documentoUsuarioAccesoRepository;
    private final DocumentoAccessService documentoAccessService;
    private final InternalNotificationService internalNotificationService;
    private final TipoDocumentoRepository tiposDocumento;
    private static final List<String> CATEGORIAS_BASE = List.of("Acta", "Reunión", "Inventario", "Proyecto", "Informe", "Oficio", "Memorando", "Resolución", "Contrato");
    private final TareaAccessService tareaAccessService;
    private final TareaAdjuntoRepository tareaAdjuntoRepository;

    private final Path uploadDir = Paths.get("uploads/documentos");
    @Value("${app.preview.libreoffice-command}")
    private String libreOfficeCommand;

    public DocumentoController(DocumentoRepository documentoRepository,
                               CarpetaRepository carpetaRepository,
                               CarpetaUsuarioRepository carpetaUsuarioRepository,
                               DocumentoVersionRepository versionRepository,
                               AuditLogRepository auditLogRepository, UsuarioRepository usuarioRepository,
                               OrgUnitRepository orgUnitRepository,
                               DocumentoSolicitudAprobacionRepository solicitudAprobacionRepository,
                               DocumentoUsuarioAccesoRepository documentoUsuarioAccesoRepository,
                               DocumentoAccessService documentoAccessService,
                               InternalNotificationService internalNotificationService,
                               TipoDocumentoRepository tiposDocumento,
                               TareaAccessService tareaAccessService,
                               TareaAdjuntoRepository tareaAdjuntoRepository) {
        this.documentoRepository = documentoRepository;
        this.carpetaRepository = carpetaRepository;
        this.carpetaUsuarioRepository = carpetaUsuarioRepository;
        this.versionRepository = versionRepository;
        this.auditLogRepository = auditLogRepository;
        this.usuarioRepository = usuarioRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.solicitudAprobacionRepository = solicitudAprobacionRepository;
        this.documentoUsuarioAccesoRepository = documentoUsuarioAccesoRepository;
        this.documentoAccessService = documentoAccessService;
        this.internalNotificationService = internalNotificationService;
        this.tiposDocumento = tiposDocumento;
        this.tareaAccessService = tareaAccessService;
        this.tareaAdjuntoRepository = tareaAdjuntoRepository;
        try { Files.createDirectories(uploadDir); } catch (IOException e) {}
    }

    /* ---- Documentos ---- */
    @GetMapping("/categorias")
    @PreAuthorize("hasAnyAuthority('Documentos:Ver','Documentos:Crear','Documentos:Editar')")
    public List<String> categorias() {
        Map<String, String> unicas = new LinkedHashMap<>();
        CATEGORIAS_BASE.forEach(nombre -> unicas.put(claveCategoria(nombre), nombre));
        tiposDocumento.findByActivoTrueOrderByNombreAsc().stream().filter(tipo -> tipo.getNombre() != null && !tipo.getNombre().isBlank()).forEach(tipo ->
            unicas.putIfAbsent(claveCategoria(tipo.getNombre()), tipo.getNombre().trim()));
        return List.copyOf(unicas.values());
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public List<Map<String, Object>> listarDocumentos() {
        Usuario actual = usuarioActual();
        List<Documento> documentos = documentoRepository.findAllByOrderByFechaDesc();
        Map<Long, DocumentoAccessService.Acceso> accesos = documentoAccessService.evaluarListado(documentos, actual);
        List<Documento> visibles = documentos.stream().filter(d -> accesos.get(d.getId()).ver()).toList();
        Map<Long, DocumentoSolicitudAprobacion> pendientes = solicitudesPendientes(visibles);
        List<Map<String, Object>> resultado = new ArrayList<>(visibles.stream()
            .map(documento -> toDocumentoMap(documento, actual, pendientes.get(documento.getId()), accesos.get(documento.getId())))
            .toList());
        resultado.addAll(adjuntosDocumentalesDeTareas());
        resultado.sort(Comparator.comparing(
            item -> Objects.toString(item.get("fecha"), ""), Comparator.reverseOrder()));
        return resultado;
    }

    @PostMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAuthority('Documentos:Crear')")
    public ResponseEntity<Map<String, Object>> crearDocumento(@RequestBody Map<String, Object> body) {
        Usuario propietario = usuarioActual();
        Documento d = new Documento();
        applyDocumentoBody(d, body);
        validarDocumento(d);
        UUID creacionId;
        try { creacionId = UUID.fromString(Objects.toString(body.get("creacionId"), "")); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Se requiere un identificador válido de creación", ex); }
        if (!creacionId.toString().equalsIgnoreCase(Objects.toString(body.get("creacionId"), ""))) throw new IllegalArgumentException("El identificador de creación debe tener formato UUID");
        documentoRepository.bloquearCreacion((propietario.getId() + ":" + creacionId).hashCode());
        Optional<Documento> existente = documentoRepository.findByPropietarioUsuarioIdAndCreacionId(propietario.getId(), creacionId);
        if (existente.isPresent()) {
            if (!mismaCreacion(existente.get(), d)) throw new IllegalStateException("El identificador de creación ya fue utilizado con otros datos");
            return ResponseEntity.ok(toDocumentoMap(existente.get()));
        }
        d.setCreacionId(creacionId);
        d.setPropietarioUsuarioId(propietario.getId());
        d.setPropietario(nombreCompleto(propietario));
        if (!documentoAccessService.canModify(d, propietario)) throw new org.springframework.security.access.AccessDeniedException("La cuenta no puede crear documentos");
        if (d.getFecha() == null) d.setFecha(LocalDate.now());
        Documento saved = documentoRepository.save(d);
        registrarAudit("CREAR", saved.getId(), "Documento \"" + saved.getNombre() + "\" creado");
        return ResponseEntity.ok(toDocumentoMap(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAnyAuthority('Documentos:Crear','Documentos:Editar')")
    public ResponseEntity<Map<String, Object>> actualizarDocumento(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return documentoRepository.findByIdForUpdate(id).map(d -> {
            if (!documentoAccessService.canModify(d, usuarioActual())) {
                return ResponseEntity.status(403).<Map<String, Object>>build();
            }
            List<String> cambios = detectarCambios(d, body);
            applyDocumentoBody(d, body);
            validarDocumento(d);
            d.setUpdatedAt(LocalDateTime.now());
            documentoRepository.save(d);
            String detalle = cambios.isEmpty() ? "Documento actualizado" : "Cambios: " + String.join(", ", cambios);
            registrarAudit("ACTUALIZAR", id, detalle);
            return ResponseEntity.ok(toDocumentoMap(d));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAnyAuthority('Documentos:Crear','Documentos:Eliminar')")
    public ResponseEntity<Void> eliminarDocumento(@PathVariable Long id) {
        Optional<Documento> opt = documentoRepository.findByIdForUpdate(id);
        if (opt.isPresent()) {
            Documento d = opt.get();
            Usuario actual = usuarioActual();
            if (!documentoAccessService.canDelete(d, actual)) return ResponseEntity.status(403).build();
            notificarEliminacion(d, actual);
            // La retirada es lógica: conserva archivos, versiones y decisiones históricas.
            documentoRepository.deleteById(id);
            registrarAudit("ELIMINAR", id, "Documento \"" + d.getNombre() + "\" eliminado");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/solicitar-aprobacion")
    @Transactional
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public ResponseEntity<Map<String, Object>> crearSolicitudAprobacion(@PathVariable Long id, @RequestBody DocumentoSolicitudAprobacionRequest request) {
        return documentoRepository.findByIdForUpdate(id).map(documento -> {
            if (!documentoAccessService.canModify(documento, usuarioActual())) return ResponseEntity.status(403).<Map<String, Object>>build();
            DocumentoVersion version = asegurarVersionActual(documento);
            if (version == null) return ResponseEntity.status(409).body(Map.<String, Object>of("error", "Adjunta un archivo válido antes de solicitar aprobación."));
            if (request.versionId() == null || !request.versionId().equals(version.getId())) return ResponseEntity.status(409).body(Map.<String, Object>of("error", "Actualiza el historial y revisa la versión actual antes de solicitar aprobación."));
            if (request.aprobadorId() == null) return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "Debes seleccionar un aprobador."));
            Usuario solicitante = usuarioActual();
            Usuario aprobador = usuarioRepository.findById(request.aprobadorId()).filter(Usuario::getActivo)
                .filter(u -> !Boolean.TRUE.equals(u.getBloqueado())).orElse(null);
            if (aprobador == null) return ResponseEntity.unprocessableContent().body(Map.<String, Object>of("error", "El aprobador seleccionado no está disponible."));
            if (solicitante.getId().equals(aprobador.getId())) return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "No puedes solicitar tu propia aprobación."));
            if (documento.getClasificacion().politica() == DocumentoClasificacion.RESTRINGIDO && !documentoAccessService.canView(documento, aprobador)) return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "El aprobador debe tener acceso a la unidad responsable del documento."));
            DocumentoSolicitudAprobacion pendiente = solicitudAprobacionRepository.findFirstByDocumentoIdAndEstadoOrderByFechaSolicitudDesc(id, "PENDIENTE").orElse(null);
            if (pendiente != null && pendiente.getVersion() != null) return ResponseEntity.status(409).body(Map.<String, Object>of("error", "El documento ya tiene una solicitud de aprobación pendiente."));
            if (pendiente != null) sustituirSolicitud(pendiente, "Solicitud anterior sin versión verificable; sustituida por una revisión del archivo actual.");
            DocumentoSolicitudAprobacion solicitud = new DocumentoSolicitudAprobacion();
            solicitud.setDocumento(documento); solicitud.setSolicitante(solicitante); solicitud.setAprobador(aprobador);
            solicitud.setVersion(version);
            solicitud.setComentarioSolicitud(request.comentario() == null ? "" : request.comentario().trim());
            solicitudAprobacionRepository.save(solicitud);
            documento.setEstado("en_revision"); documento.setUpdatedAt(LocalDateTime.now()); documentoRepository.save(documento);
            crearNotificacion(aprobador, "Aprobación de documento solicitada", "" + nombreCompleto(solicitante) + " solicitó tu aprobación para \"" + documento.getNombre() + "\".");
            registrarAudit("SOLICITAR_APROBACION", id, "Solicitud " + solicitud.getId() + " enviada a " + nombreCompleto(aprobador));
            return ResponseEntity.ok(toDocumentoMap(documento));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/resolver-aprobacion")
    @Transactional
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public ResponseEntity<Map<String, Object>> resolverSolicitudAprobacion(@PathVariable Long id, @RequestBody DocumentoResolucionAprobacionRequest request) {
        String decision = request.decision() == null ? "" : request.decision().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("aprobar", "rechazar").contains(decision)) return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "La decisión debe ser aprobar o rechazar."));
        return documentoRepository.findByIdForUpdate(id).map(documento -> {
            if (!puedeVerDocumento(documento)) return ResponseEntity.status(403).<Map<String, Object>>build();
            DocumentoSolicitudAprobacion solicitud = solicitudAprobacionRepository.findFirstByDocumentoIdAndEstadoOrderByFechaSolicitudDesc(id, "PENDIENTE").orElse(null);
            if (solicitud == null) return ResponseEntity.status(409).body(Map.<String, Object>of("error", "La solicitud ya fue procesada o no existe."));
            Usuario actual = usuarioActual();
            if (!esAdmin() && !actual.getId().equals(solicitud.getAprobador().getId())) return ResponseEntity.status(403).body(Map.<String, Object>of("error", "Solo el aprobador seleccionado puede responder esta solicitud."));
            if (solicitud.getVersion() == null || request.solicitudId() == null || request.versionId() == null
                    || !solicitud.getId().equals(request.solicitudId()) || !solicitud.getVersion().getId().equals(request.versionId())
                    || !request.versionId().equals(documento.getVersionActualId())) return ResponseEntity.status(409).body(Map.<String, Object>of("error", "La solicitud no corresponde a la versión actual. Actualiza el historial antes de responder."));
            solicitud.setEstado("aprobar".equals(decision) ? "APROBADA" : "RECHAZADA"); solicitud.setRespondidoPor(actual); solicitud.setFechaRespuesta(LocalDateTime.now()); solicitud.setComentarioRespuesta(request.comentario() == null ? "" : request.comentario().trim()); solicitudAprobacionRepository.save(solicitud);
            documento.setEstado("aprobar".equals(decision) ? "aprobado" : "rechazado"); documento.setUpdatedAt(LocalDateTime.now()); documentoRepository.save(documento);
            crearNotificacion(solicitud.getSolicitante(), "aprobar".equals(decision) ? "Documento aprobado" : "Documento rechazado", "" + nombreCompleto(actual) + " " + ("aprobar".equals(decision) ? "aprobó" : "rechazó") + " el documento \"" + documento.getNombre() + "\".");
            registrarAudit("RESOLVER_APROBACION", id, "Solicitud " + solicitud.getId() + " " + solicitud.getEstado() + " por " + nombreCompleto(actual));
            return ResponseEntity.ok(toDocumentoMap(documento));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ---- Archivos ---- */
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "jpg", "jpeg", "png", "gif",
        "txt", "csv", "zip", "rar"
    );
    private static final java.util.Set<String> TASK_DOCUMENT_EXTENSIONS = java.util.Set.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv"
    );

    @PostMapping(value = "/{id}/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    @PreAuthorize("hasAnyAuthority('Documentos:Crear','Documentos:Editar')")
    public ResponseEntity<Map<String, Object>> subirArchivo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Optional<Documento> opt = documentoRepository.findByIdForUpdate(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!documentoAccessService.canModify(opt.get(), usuarioActual())) return ResponseEntity.status(403).build();
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("error", "El archivo debe contener datos y pesar como máximo 10 MB"));
        try { FileContentValidator.validate(file, ALLOWED_EXTENSIONS); }
        catch (IOException ex) { return ResponseEntity.badRequest().body(Map.of("error", "El archivo no pasó la validación de formato o contenido.")); }
        try {
            Documento d = opt.get();
            String hash;
            try (InputStream input = file.getInputStream()) { hash = hashContenido(input); }
            DocumentoVersion actual = asegurarVersionActual(d);
            if (actual == null && d.getArchivoRuta() != null && !d.getArchivoRuta().isBlank()
                    && versionRepository.findByDocumentoIdOrderByIdDesc(id).stream().noneMatch(v -> Objects.equals(v.getVersion(), d.getVersion()))) {
                DocumentoVersion referencia = new DocumentoVersion(); referencia.setDocumentoId(id); referencia.setVersion(d.getVersion());
                referencia.setAutor(nombreAutorVersion(usuarioActual())); referencia.setAutorId(usuarioActual().getId());
                referencia.setFecha(LocalDate.now()); referencia.setRegistradaEn(LocalDateTime.now());
                referencia.setCambios("Versión declarada antes del reemplazo; el archivo anterior no estaba disponible y no se atribuye contenido a este registro.");
                versionRepository.save(referencia);
            }
            if (actual != null && hash.equals(actual.getContenidoHash())) {
                if (!archivoDisponible(actual.getArchivoRuta())) {
                    Path restaurado = UploadStorage.storeTransactional(uploadDir, file, null);
                    actual.setArchivoRuta(restaurado.toString()); versionRepository.save(actual);
                    d.setArchivoRuta(restaurado.toString()); d.setUpdatedAt(LocalDateTime.now()); documentoRepository.save(d);
                    registrarAudit("RECUPERAR_ARCHIVO", id, "Copia recuperada mediante SHA-256; se conserva la versión " + actual.getVersion());
                }
                Map<String, Object> respuesta = toDocumentoMap(d);
                respuesta.put("contenidoIdentico", true);
                return ResponseEntity.ok(respuesta);
            }
            // Sin ruta anterior: la limpieza transaccional solo retira el nuevo archivo si hay rollback.
            Path targetPath = UploadStorage.storeTransactional(uploadDir, file, null);
            int numero = Math.addExact(versionRepository.ultimoNumero(id), 1);
            DocumentoVersion nueva = registrarVersion(d, targetPath, nombreArchivo(file.getOriginalFilename()), hash, numero, false);
            d.setArchivoRuta(targetPath.toString());
            d.setTamano(formatFileSize(file.getSize()));
            d.setPaginas(nueva.getPaginas());
            d.setVersion(nueva.getVersion());
            d.setVersionActualId(nueva.getId());
            d.setEstado("borrador");
            d.setAprobadorId(null); d.setSolicitadoPorId(null);
            d.setUpdatedAt(LocalDateTime.now());
            solicitudAprobacionRepository.findFirstByDocumentoIdAndEstadoOrderByFechaSolicitudDesc(id, "PENDIENTE")
                .ifPresent(s -> sustituirSolicitud(s, "El contenido fue reemplazado; esta solicitud no aprueba la nueva versión."));
            documentoRepository.save(d);
            registrarAudit("ACTUALIZAR_ARCHIVO", id, "Versión " + nueva.getVersion() + " registrada; requiere aprobación de su contenido");
            Map<String, Object> m = toDocumentoMap(d);
            m.put("contenidoIdentico", false);
            return ResponseEntity.ok(m);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo conservar el archivo del documento", e);
        }
    }

    @GetMapping("/{id}/versiones")
    @Transactional
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public ResponseEntity<Map<String, Object>> historialVersiones(@PathVariable Long id) {
        Documento documento = documentoRepository.findByIdForUpdate(id).orElse(null);
        if (documento == null || !puedeVerDocumento(documento)) return ResponseEntity.notFound().build();
        asegurarVersionActual(documento);
        List<DocumentoSolicitudAprobacion> solicitudes = solicitudAprobacionRepository.findByDocumentoIdOrderByFechaSolicitudDescIdDesc(id);
        List<Map<String, Object>> versiones = new ArrayList<>(), antecedentes = new ArrayList<>();
        for (DocumentoVersion version : versionRepository.findByDocumentoIdOrderByIdDesc(id)) {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("id", version.getId()); detalle.put("version", version.getVersion());
            detalle.put("autor", version.getAutor()); detalle.put("autorId", version.getAutorId());
            detalle.put("fecha", version.getRegistradaEn() == null ? (version.getFecha() == null ? null : version.getFecha().toString()) : version.getRegistradaEn().toString());
            detalle.put("lineaBase", version.isLineaBase()); detalle.put("cambios", version.getCambios());
            detalle.put("archivoNombre", version.getArchivoNombre()); detalle.put("tamanoBytes", version.getTamanoBytes());
            detalle.put("actual", version.getId().equals(documento.getVersionActualId()));
            boolean disponible = archivoDisponible(version.getArchivoRuta());
            detalle.put("disponible", disponible);
            detalle.put("descargaUrl", disponible ? "/api/documentos/" + id + "/versiones/" + version.getId() + "/descargar" : null);
            detalle.put("aprobaciones", solicitudes.stream().filter(s -> s.getVersion() != null && s.getVersion().getId().equals(version.getId())).map(this::solicitudMap).toList());
            if (version.getNumero() == null) antecedentes.add(detalle); else versiones.add(detalle);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("documento", toDocumentoMap(documento)); respuesta.put("versiones", versiones);
        respuesta.put("registrosAnteriores", antecedentes);
        respuesta.put("aprobacionesSinVersion", solicitudes.stream().filter(s -> s.getVersion() == null).map(this::solicitudMap).toList());
        respuesta.put("archivoActualDisponible", archivoDisponible(documento.getArchivoRuta()));
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{id}/versiones/{versionId}/descargar")
    @PreAuthorize("hasAuthority('Documentos:Descargar')")
    public ResponseEntity<org.springframework.core.io.Resource> descargarVersion(@PathVariable Long id, @PathVariable Long versionId) {
        Documento documento = documentoRepository.findById(id).orElse(null);
        if (documento == null || !puedeVerDocumento(documento)) return ResponseEntity.notFound().build();
        DocumentoVersion version = versionRepository.findByIdAndDocumentoId(versionId, id).orElse(null);
        if (version == null || !archivoDisponible(version.getArchivoRuta())) return ResponseEntity.notFound().build();
        try {
            var resource = new org.springframework.core.io.UrlResource(UploadStorage.resolveInside(uploadDir, version.getArchivoRuta()).toUri());
            return ResponseEntity.ok().contentType(detectMediaType(version.getArchivoNombre()))
                .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment()
                    .filename(version.getArchivoNombre(), java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(resource);
        } catch (IOException | IllegalArgumentException failure) { return ResponseEntity.notFound().build(); }
    }

    private DocumentoVersion asegurarVersionActual(Documento documento) {
        if (documento.getVersionActualId() != null) return versionRepository.findByIdAndDocumentoId(documento.getVersionActualId(), documento.getId())
            .orElseThrow(() -> new IllegalStateException("No se pudo identificar la versión actual del documento."));
        if (!archivoDisponible(documento.getArchivoRuta())) return null;
        try {
            Path archivo = UploadStorage.resolveInside(uploadDir, documento.getArchivoRuta());
            String hash;
            try (InputStream input = Files.newInputStream(archivo)) { hash = hashContenido(input); }
            int numero = Math.max(1, versionRepository.ultimoNumero(documento.getId()) + 1);
            if (documento.getVersion() != null && documento.getVersion().matches("[0-9]{1,9}(\\.[0-9]+)*")) {
                numero = Math.max(numero, Integer.parseInt(documento.getVersion().split("\\.")[0]));
            }
            DocumentoVersion base = registrarVersion(documento, archivo, nombreArchivo(archivo.getFileName().toString()), hash, numero, true);
            if (documento.getVersion() != null && !documento.getVersion().isBlank()) base.setVersion(documento.getVersion());
            versionRepository.save(base);
            documento.setVersionActualId(base.getId()); documentoRepository.save(documento);
            registrarAudit("REGISTRAR_VERSION_BASE", documento.getId(), "Archivo actual verificado; no se atribuye fecha ni autor original a versiones anteriores");
            return base;
        } catch (IOException failure) { throw new UncheckedIOException("No se pudo verificar el archivo actual", failure); }
    }

    private DocumentoVersion registrarVersion(Documento documento, Path archivo, String nombre, String hash, int numero, boolean base) throws IOException {
        Usuario actor = usuarioActual();
        DocumentoVersion version = new DocumentoVersion();
        version.setDocumentoId(documento.getId()); version.setNumero(numero); version.setVersion(numero + ".0");
        version.setArchivoRuta(archivo.toString()); version.setArchivoNombre(nombre); version.setContenidoHash(hash);
        version.setTamanoBytes(Files.size(archivo)); version.setPaginas(base ? documento.getPaginas() : calcularNumeroPaginas(archivo));
        version.setAutor(nombreAutorVersion(actor));
        version.setAutorId(actor.getId());
        version.setFecha(LocalDate.now()); version.setRegistradaEn(LocalDateTime.now()); version.setLineaBase(base);
        version.setCambios(base ? "Archivo existente verificado en esta fecha. Autor y fecha originales no comprobados." : "Contenido registrado como nueva versión.");
        return versionRepository.saveAndFlush(version);
    }

    private boolean archivoDisponible(String ruta) {
        if (ruta == null || ruta.isBlank()) return false;
        try { return Files.isRegularFile(UploadStorage.resolveInside(uploadDir, ruta)); }
        catch (IllegalArgumentException failure) { return false; }
    }

    private static String hashContenido(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; int leidos;
            while ((leidos = input.read(buffer)) != -1) digest.update(buffer, 0, leidos);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("No se dispone del verificador de contenido", failure); }
    }

    private static String nombreArchivo(String original) {
        String nombre = original == null ? "archivo" : original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (nombre.isBlank()) nombre = "archivo";
        int caracteres = nombre.codePointCount(0, nombre.length());
        return caracteres > 255 ? nombre.substring(nombre.offsetByCodePoints(0, caracteres - 255)) : nombre;
    }

    private String nombreAutorVersion(Usuario usuario) {
        String autor = nombreCompleto(usuario);
        return autor.codePointCount(0, autor.length()) > 200 ? autor.substring(0, autor.offsetByCodePoints(0, 200)) : autor;
    }

    private void sustituirSolicitud(DocumentoSolicitudAprobacion solicitud, String motivo) {
        solicitud.setEstado("SUSTITUIDA"); solicitud.setComentarioRespuesta(motivo);
        solicitud.setRespondidoPor(usuarioActual()); solicitud.setFechaRespuesta(LocalDateTime.now());
        solicitudAprobacionRepository.saveAndFlush(solicitud);
        registrarAudit("SUSTITUIR_APROBACION", solicitud.getDocumento().getId(), motivo);
    }

    @GetMapping("/{id}/descargar")
    @PreAuthorize("hasAuthority('Documentos:Descargar')")
    public ResponseEntity<org.springframework.core.io.Resource> descargarArchivo(@PathVariable Long id) {
        Optional<Documento> opt = documentoRepository.findById(id);
        if (opt.isEmpty() || !puedeVerDocumento(opt.get()) || opt.get().getArchivoRuta() == null) return ResponseEntity.notFound().build();
        try {
            Path filePath = UploadStorage.resolveInside(uploadDir, opt.get().getArchivoRuta());
            org.springframework.core.io.UrlResource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists()) {
                String nombre = opt.get().getNombre();
                MediaType mediaType = detectMediaType(opt.get().getArchivoRuta());
                return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header("Content-Disposition", org.springframework.http.ContentDisposition.inline().filename(nombre.replaceAll("[\\r\\n]", ""), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                    .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/vista-previa")
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public ResponseEntity<byte[]> vistaPrevia(@PathVariable Long id) {
        Documento documento = documentoRepository.findById(id).filter(this::puedeVerDocumento).orElse(null);
        if (documento == null || documento.getArchivoRuta() == null) return ResponseEntity.notFound().build();
        try {
            Path archivo = UploadStorage.resolveInside(uploadDir, documento.getArchivoRuta());
            String extension = extension(archivo.getFileName().toString());
            if (Set.of("pdf", "png", "jpg", "jpeg", "gif", "webp", "txt", "csv").contains(extension)) {
                return ResponseEntity.ok().contentType(detectMediaType(archivo.toString())).header("Content-Disposition", "inline").body(Files.readAllBytes(archivo));
            }
            if (libreOfficeCommand.isBlank()) return ResponseEntity.status(503).body(null);
            Path salida = Files.createTempDirectory("sigaa-preview-");
            try {
                Path perfil = Files.createDirectories(salida.resolve("libreoffice-profile"));
                Process proceso = new ProcessBuilder(comandoLibreOffice(), "-env:UserInstallation=" + perfil.toUri(), "--headless", "--convert-to", "pdf", "--outdir", salida.toString(), archivo.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!proceso.waitFor(45, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                    return ResponseEntity.unprocessableContent().build();
                }
                if (proceso.exitValue() != 0) return ResponseEntity.unprocessableContent().build();
                try (var archivos = Files.list(salida)) {
                    Path pdf = archivos.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")).findFirst().orElse(null);
                    if (pdf == null) return ResponseEntity.unprocessableContent().build();
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition", "inline").body(Files.readAllBytes(pdf));
                }
            } finally {
                try (var archivos = Files.walk(salida)) { archivos.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); }
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return ResponseEntity.internalServerError().build(); }
        catch (IOException ex) { return ResponseEntity.internalServerError().build(); }
    }

    private String extension(String nombre) { int punto=nombre.lastIndexOf('.'); return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT); }

    private List<Map<String, Object>> adjuntosDocumentalesDeTareas() {
        List<Tarea> tareas = tareaAccessService.tareasVisiblesActual();
        if (tareas.isEmpty()) return List.of();
        Map<Long, Tarea> porId = tareas.stream().collect(java.util.stream.Collectors.toMap(
            Tarea::getId, tarea -> tarea, (primera, ignorada) -> primera, LinkedHashMap::new));
        return tareaAdjuntoRepository.findByTareaIdInOrderById(porId.keySet()).stream()
            .filter(adjunto -> adjunto.getRutaArchivo() != null && !adjunto.getRutaArchivo().isBlank())
            .filter(adjunto -> TASK_DOCUMENT_EXTENSIONS.contains(extension(adjunto.getNombre())))
            .map(adjunto -> toTareaAdjuntoMap(adjunto, porId.get(adjunto.getTareaId())))
            .filter(Objects::nonNull)
            .toList();
    }

    private Map<String, Object> toTareaAdjuntoMap(TareaAdjunto adjunto, Tarea tarea) {
        if (tarea == null) return null;
        String descargaUrl = "/api/tareas/" + tarea.getId() + "/adjuntos/" + adjunto.getId() + "/descargar";
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "tarea-" + adjunto.getId());
        item.put("nombre", adjunto.getNombre());
        item.put("categoria", "Tarea");
        item.put("area", tarea.getUnidades() == null || tarea.getUnidades().isBlank() ? "Tareas" : tarea.getUnidades());
        item.put("version", "");
        item.put("fecha", tarea.getFechaCreacion() == null ? "" : tarea.getFechaCreacion().toString());
        item.put("propietario", tarea.getCreadoPor() == null ? "" : tarea.getCreadoPor());
        item.put("propietarioId", null);
        item.put("clasificacion", DocumentoClasificacion.USO_INTERNO.name());
        item.put("estado", "borrador");
        item.put("descripcion", "Adjunto de la tarea \"" + tarea.getTitulo() + "\"");
        item.put("paginas", 0);
        item.put("tamano", adjunto.getTamano() == null ? "" : adjunto.getTamano());
        item.put("compartido", false);
        item.put("archivoRuta", descargaUrl);
        item.put("descargaUrl", descargaUrl);
        item.put("vistaPreviaUrl", null);
        item.put("permiso", "solo_lectura");
        item.put("carpetaId", null);
        item.put("puedeEditar", false);
        item.put("puedeDescargar", true);
        item.put("puedeAdministrarAcceso", false);
        item.put("puedeEliminar", false);
        item.put("solicitudAprobacion", null);
        item.put("origen", "TAREA");
        item.put("tareaId", String.valueOf(tarea.getId()));
        item.put("tareaCodigo", tarea.getCodigo());
        item.put("tareaTitulo", tarea.getTitulo());
        return item;
    }

    private int calcularNumeroPaginas(Path archivo) {
        String ext = extension(archivo.getFileName().toString());
        if (Set.of("png", "jpg", "jpeg", "gif", "webp").contains(ext)) return 1;
        try {
            if ("pdf".equals(ext)) return contarPaginasPdf(archivo);
            if (!Set.of("doc", "docx", "ppt", "pptx", "xls", "xlsx").contains(ext)
                    || libreOfficeCommand == null || libreOfficeCommand.isBlank()) return 0;

            Path salida = Files.createTempDirectory("sigaa-pages-");
            try {
                Path perfil = Files.createDirectories(salida.resolve("libreoffice-profile"));
                Process proceso = new ProcessBuilder(comandoLibreOffice(), "-env:UserInstallation=" + perfil.toUri(), "--headless", "--convert-to", "pdf", "--outdir", salida.toString(), archivo.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!proceso.waitFor(45, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                    return 0;
                }
                if (proceso.exitValue() != 0) return 0;
                try (var archivos = Files.list(salida)) {
                    Path pdf = archivos.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")).findFirst().orElse(null);
                    return pdf == null ? 0 : contarPaginasPdf(pdf);
                }
            } finally {
                eliminarDirectorioTemporal(salida);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException ex) {
            return 0;
        }
    }

    private int contarPaginasPdf(Path archivo) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(archivo.toFile())) {
            return pdf.getNumberOfPages();
        }
    }

    private String comandoLibreOffice() {
        Path configurado = Paths.get(libreOfficeCommand);
        if (configurado.getFileName() != null && "soffice.exe".equalsIgnoreCase(configurado.getFileName().toString())) {
            Path consola = configurado.resolveSibling("soffice.com");
            if (Files.isRegularFile(consola)) return consola.toString();
        }
        return libreOfficeCommand;
    }

    private void eliminarDirectorioTemporal(Path directorio) {
        try (var archivos = Files.walk(directorio)) {
            archivos.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private MediaType detectMediaType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".txt") || lower.endsWith(".csv")) return MediaType.TEXT_PLAIN;
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return MediaType.parseMediaType("application/msword");
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return MediaType.parseMediaType("application/vnd.ms-excel");
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return MediaType.parseMediaType("application/vnd.ms-powerpoint");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /* ---- Carpetas ---- */
    @GetMapping("/carpetas")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public List<Map<String, Object>> listarCarpetas(@RequestParam(defaultValue = "true") boolean incluirDocumentos) {
        Usuario actual = usuarioActual();
        List<Carpeta> visibles = documentoAccessService.filtrarCarpetas(carpetaRepository.findAllByOrderByNombreAsc(), actual);
        Set<Long> ids = visibles.stream().map(Carpeta::getId).collect(java.util.stream.Collectors.toSet());
        List<Documento> candidatos = ids.isEmpty() ? List.of() : documentoRepository.findByCarpetaIdInOrderByFechaDesc(ids);
        Map<Long, DocumentoAccessService.Acceso> accesos = documentoAccessService.evaluarListado(candidatos, actual);
        List<Documento> documentos = candidatos.stream().filter(d -> accesos.get(d.getId()).ver()).toList();
        Map<Long, DocumentoSolicitudAprobacion> pendientes = incluirDocumentos ? solicitudesPendientes(documentos) : Map.of();
        Map<Long, List<Documento>> porCarpeta = documentos.stream().collect(java.util.stream.Collectors.groupingBy(Documento::getCarpetaId));
        return visibles.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(c.getId()));
            m.put("nombre", c.getNombre());
            m.put("tipo", c.getTipo());
            m.put("propietario", c.getPropietario() == null ? "" : c.getPropietario().getNombre() + " " + c.getPropietario().getApellido());
            m.put("puedeEliminar", documentoAccessService.canDeleteFolder(c, actual));
            List<Documento> docs = porCarpeta.getOrDefault(c.getId(), List.of());
            m.put("documentos", incluirDocumentos ? docs.stream().map(d -> toDocumentoMap(d, actual, pendientes.get(d.getId()), accesos.get(d.getId()))).toList() : List.of());
            m.put("count", docs.size());
            return m;
        }).toList();
    }

    @PostMapping("/carpetas")
    @PreAuthorize("hasAuthority('Documentos:Crear carpeta')")
    @Transactional public ResponseEntity<Map<String, Object>> crearCarpeta(@RequestBody Map<String, Object> body) {
        Carpeta c = new Carpeta();
        if (!(body.get("nombre") instanceof String nombre) || nombre.isBlank() || nombre.length() > 300) throw new IllegalArgumentException("El nombre de la carpeta debe contener entre 1 y 300 caracteres");
        if (!Set.of("PERSONAL", "COMPARTIDA").contains(Objects.toString(body.getOrDefault("tipo", "PERSONAL"), "").toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Tipo de carpeta inválido");
        c.setNombre(nombre.trim());
        c.setTipo("COMPARTIDA".equalsIgnoreCase(String.valueOf(body.getOrDefault("tipo", "PERSONAL"))) ? "COMPARTIDA" : "PERSONAL");
        c.setPropietario(usuarioActual());
        Carpeta saved = carpetaRepository.save(c);
        if ("COMPARTIDA".equals(saved.getTipo()) && body.get("miembroIds") instanceof List<?> ids) for (Object id : ids) {
            Long usuarioId = Long.valueOf(String.valueOf(id));
            usuarioRepository.findById(usuarioId).filter(Usuario::getActivo).ifPresent(u -> { CarpetaUsuario member = new CarpetaUsuario(); member.setCarpeta(saved); member.setUsuario(u); carpetaUsuarioRepository.save(member); });
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(saved.getId()));
        m.put("nombre", saved.getNombre());
        m.put("tipo", saved.getTipo());
        m.put("documentos", List.of());
        m.put("count", 0);
        m.put("puedeEliminar", true);
        registrarAuditCarpeta("CREAR", saved.getId(), "Carpeta creada");
        return ResponseEntity.ok(m);
    }

    @DeleteMapping("/carpetas/{id}")
    @PreAuthorize("hasAuthority('Documentos:Crear carpeta')")
    @Transactional
    public ResponseEntity<Void> eliminarCarpeta(@PathVariable Long id) {
        Carpeta carpeta = carpetaRepository.findById(id).orElse(null);
        if (carpeta == null) return ResponseEntity.notFound().build();
        Usuario actual = usuarioActual();
        if (!documentoAccessService.canDeleteFolder(carpeta, actual)) {
            return ResponseEntity.status(403).build();
        }

        List<Documento> documentos = documentoRepository.findByCarpetaIdOrderByFechaDesc(id);
        // El borrado lógico de documentos preserva sus archivos y aprobaciones.
        for (Documento documento : documentos) {
            notificarEliminacion(documento, actual);
        }
        documentoRepository.deleteAll(documentos);
        carpetaRepository.delete(carpeta);
        registrarAuditCarpeta("ELIMINAR", id,
            "Carpeta \"" + carpeta.getNombre() + "\" eliminada con " + documentos.size() + " documento(s)");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/carpetas/usuarios")
    @PreAuthorize("hasAuthority('Documentos:Crear carpeta')")
    public List<Map<String, Object>> usuariosParaCarpeta() {
        return usuarioRepository.findByActivoTrue().stream().filter(u -> !Boolean.TRUE.equals(u.getBloqueado())).map(u -> Map.<String, Object>of("id", u.getId(), "nombre", u.getNombre() + " " + u.getApellido(), "correo", u.getCorreo())).toList();
    }

    /* ---- Compartición explícita ---- */
    @GetMapping("/{id}/compartidos")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public ResponseEntity<List<Map<String, Object>>> listarCompartidos(@PathVariable Long id) {
        Documento documento = documentoRepository.findById(id).orElse(null);
        if (documento == null) return ResponseEntity.notFound().build();
        if (!documentoAccessService.canManageAccess(documento, usuarioActual())) return ResponseEntity.status(403).build();
        List<Map<String, Object>> accesos = documentoUsuarioAccesoRepository.findByDocumentoIdOrderByCreadoEnAsc(id).stream()
                .filter(acceso -> acceso.getExpiraEn() == null || acceso.getExpiraEn().isAfter(LocalDateTime.now())).map(acceso -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("usuarioId", acceso.getUsuario().getId());
            item.put("nombre", nombreCompleto(acceso.getUsuario()));
            item.put("correo", acceso.getUsuario().getCorreo());
            item.put("permiso", acceso.getPermiso());
            item.put("expiraEn", acceso.getExpiraEn());
            return item;
        }).toList();
        return ResponseEntity.ok(accesos);
    }

    @PostMapping("/{id}/compartidos")
    @Transactional
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public ResponseEntity<Map<String, Object>> compartirDocumento(@PathVariable Long id,
                                                                  @Valid @RequestBody DocumentoCompartirRequest request) {
        Documento documento = documentoRepository.findByIdForUpdate(id).orElse(null);
        if (documento == null) return ResponseEntity.notFound().build();
        Usuario actual = usuarioActual();
        if (!documentoAccessService.canManageAccess(documento, actual)) return ResponseEntity.status(403).build();
        Usuario destino = usuarioRepository.findById(request.usuarioId()).filter(Usuario::getActivo)
                .filter(usuario -> !Boolean.TRUE.equals(usuario.getBloqueado())).orElse(null);
        if (destino == null) return ResponseEntity.unprocessableContent().body(Map.of("error", "El usuario seleccionado no está disponible."));
        if (documento.getClasificacion().politica() == DocumentoClasificacion.RESTRINGIDO && !documentoAccessService.canView(documento, destino)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La clasificación Por unidad no permite conceder acceso fuera de la unidad responsable."));
        }
        if (destino.getId().equals(actual.getId()) || destino.getId().equals(documento.getPropietarioUsuarioId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "El propietario ya tiene acceso al documento."));
        }
        String permiso = "EDICION".equalsIgnoreCase(request.permiso()) ? "EDICION" : "LECTURA";
        if (request.fechaExpiracion() != null && request.fechaExpiracion().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La fecha de expiración no puede estar en el pasado."));
        }
        DocumentoUsuarioAcceso acceso = documentoUsuarioAccesoRepository.findByDocumentoIdAndUsuarioId(id, destino.getId())
                .orElseGet(DocumentoUsuarioAcceso::new);
        acceso.setDocumento(documento);
        acceso.setUsuario(destino);
        acceso.setPermiso(permiso);
        acceso.setExpiraEn(request.fechaExpiracion() == null ? null : request.fechaExpiracion().atTime(23, 59, 59));
        acceso.setCreadoPor(actual);
        documentoUsuarioAccesoRepository.save(acceso);
        documento.setCompartido(true);
        documento.setUpdatedAt(LocalDateTime.now());
        documentoRepository.save(documento);
        registrarAudit("COMPARTIR", id, "Acceso " + permiso + " concedido a " + destino.getCorreo());
        return ResponseEntity.ok(toDocumentoMap(documento));
    }

    @DeleteMapping("/{id}/compartidos/{usuarioId}")
    @Transactional
    @PreAuthorize("hasAuthority('Documentos:Ver')")
    public ResponseEntity<Void> retirarCompartido(@PathVariable Long id, @PathVariable Long usuarioId) {
        Documento documento = documentoRepository.findByIdForUpdate(id).orElse(null);
        if (documento == null) return ResponseEntity.notFound().build();
        if (!documentoAccessService.canManageAccess(documento, usuarioActual())) return ResponseEntity.status(403).build();
        if (documentoUsuarioAccesoRepository.findByDocumentoIdAndUsuarioId(id, usuarioId).isEmpty()) return ResponseEntity.notFound().build();
        documentoUsuarioAccesoRepository.deleteByDocumentoIdAndUsuarioId(id, usuarioId);
        documentoUsuarioAccesoRepository.flush();
        documento.setCompartido(documentoUsuarioAccesoRepository.countByDocumentoId(id) > 0);
        documento.setUpdatedAt(LocalDateTime.now());
        documentoRepository.save(documento);
        registrarAudit("RETIRAR_COMPARTICION", id, "Acceso retirado al usuario " + usuarioId);
        return ResponseEntity.noContent().build();
    }

    /* ---- Permisos ---- */
    @PatchMapping("/{id}/permiso")
    @Transactional
    @PreAuthorize("hasRole('superadmin') or (hasAnyRole('Director','Directora','Subdirector','Subdirectora') and hasAuthority('Documentos:Editar'))")
    public ResponseEntity<Map<String, Object>> actualizarPermiso(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return documentoRepository.findByIdForUpdate(id).map(d -> {
            if (!documentoAccessService.canManageAccess(d, usuarioActual())) return ResponseEntity.status(403).<Map<String, Object>>build();
            if (!Set.of("solo_lectura", "editar", "aprobar", "administrar").contains(body.getOrDefault("nivel", ""))) throw new IllegalArgumentException("Nivel de permiso inválido");
            d.setPermiso(body.getOrDefault("nivel", "solo_lectura"));
            d.setUpdatedAt(LocalDateTime.now());
            documentoRepository.save(d);
            registrarAudit("ACTUALIZAR", id, "Permiso cambiado a \"" + d.getPermiso() + "\"");
            return ResponseEntity.ok(toDocumentoMap(d));
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ---- helpers ---- */
    private void applyDocumentoBody(Documento d, Map<String, Object> body) {
        DocumentoClasificacion anterior = d.getClasificacion() == null ? DocumentoClasificacion.USO_INTERNO : d.getClasificacion();
        String unidadAnterior = d.getArea();
        for (String campo : List.of("nombre", "categoria", "area", "version", "fecha", "propietario", "estado", "descripcion", "tamano", "permiso", "versionCambios", "versionAutor")) {
            if (body.containsKey(campo) && !(body.get(campo) instanceof String)) throw new IllegalArgumentException("El campo " + campo + " debe contener texto");
        }
        if (body.containsKey("version") && !Objects.equals(d.getVersion(), body.get("version"))) throw new IllegalArgumentException("La versión se calcula automáticamente al adjuntar contenido.");
        if (body.containsKey("versionCambios") || body.containsKey("versionAutor")) throw new IllegalArgumentException("El historial de contenido se registra automáticamente.");
        for (String campo : List.of("paginas", "carpetaId", "carpeta_id")) {
            Object valor = body.get(campo);
            if (valor != null && (!(valor instanceof Number numero) || !Double.isFinite(numero.doubleValue()) || numero.doubleValue() != numero.longValue() || numero.longValue() < 0)) throw new IllegalArgumentException("El campo " + campo + " debe contener un número entero válido");
        }
        if (body.containsKey("paginas") && (!(body.get("paginas") instanceof Number numero) || numero.longValue() > Integer.MAX_VALUE)) throw new IllegalArgumentException("El número de páginas no es válido");
        if (body.containsKey("compartido") && !(body.get("compartido") instanceof Boolean)) throw new IllegalArgumentException("El campo compartido debe contener un booleano");
        if (body.containsKey("permiso") && !Set.of("solo_lectura", "editar", "aprobar", "administrar").contains(body.get("permiso"))) throw new IllegalArgumentException("Nivel de permiso inválido");
        boolean cambiaCarpeta = body.containsKey("carpetaId") || body.containsKey("carpeta_id");
        Object carpetaVal = body.containsKey("carpetaId") ? body.get("carpetaId") : body.get("carpeta_id");
        Long nuevaCarpeta = carpetaVal == null ? null : ((Number) carpetaVal).longValue();
        boolean cambiaAcceso = (cambiaCarpeta && !Objects.equals(d.getCarpetaId(), nuevaCarpeta))
            || (body.containsKey("area") && !Objects.toString(d.getArea(), "").equalsIgnoreCase(((String) body.get("area")).trim()))
            || (body.containsKey("propietario") && !Objects.equals(d.getPropietario(), body.get("propietario")))
            || (body.containsKey("clasificacion") && anterior.politica() != DocumentoClasificacion.from(body.get("clasificacion")).politica())
            || (body.containsKey("compartido") && !Objects.equals(d.getCompartido(), body.get("compartido")))
            || (body.containsKey("permiso") && !Objects.equals(d.getPermiso(), body.get("permiso")));
        if (d.getId() != null && cambiaAcceso && !documentoAccessService.canManageAccess(d, usuarioActual())) {
            throw new org.springframework.security.access.AccessDeniedException("Sin permiso para cambiar el acceso al documento");
        }
        if (body.containsKey("nombre")) d.setNombre((String) body.get("nombre"));
        if (body.containsKey("categoria")) d.setCategoria(categoriaCanonica((String) body.get("categoria"), d.getCategoria()));
        if (body.containsKey("area")) {
            String unidad = String.valueOf(body.get("area")).trim();
            if ("Personal".equalsIgnoreCase(unidad)) d.setArea("Personal");
            else d.setArea(orgUnitRepository.findFirstByNombreIgnoreCaseAndActivoTrue(unidad).orElseThrow(() -> new IllegalArgumentException("La unidad seleccionada no existe o está inactiva")).getNombre());
        }
        if (body.containsKey("fecha")) {
            try { d.setFecha(LocalDate.parse((String) body.get("fecha"))); }
            catch (java.time.format.DateTimeParseException ex) { throw new IllegalArgumentException("La fecha debe tener el formato AAAA-MM-DD", ex); }
        }
        if (body.containsKey("propietario") && (d.getId() == null || esAdmin())) d.setPropietario((String) body.get("propietario"));
        if (body.containsKey("clasificacion")) {
            DocumentoClasificacion nueva = DocumentoClasificacion.from(body.get("clasificacion"));
            if (d.getId() != null && anterior.politica() != nueva.politica() && !esAdmin()) {
                throw new org.springframework.security.access.AccessDeniedException("Solo un administrador puede reclasificar un documento existente");
            }
            if (d.getId() == null || anterior.politica() != nueva.politica()) d.setClasificacion(nueva.politica());
        }
        if (d.getClasificacion().politica() == DocumentoClasificacion.RESTRINGIDO
                && (d.getArea() == null || d.getArea().isBlank() || "Personal".equalsIgnoreCase(d.getArea()))
                && (d.getId() == null || anterior.politica() != d.getClasificacion().politica() || !Objects.equals(unidadAnterior, d.getArea()))) {
            throw new IllegalArgumentException("La clasificación Por unidad requiere una unidad institucional responsable.");
        }
        if (body.containsKey("estado")) {
            String estado = Objects.toString(body.get("estado"), "");
            if ((d.getId() == null && !"borrador".equals(estado)) || (d.getId() != null && !Objects.equals(d.getEstado(), estado))) {
                throw new IllegalArgumentException("El estado se modifica mediante el flujo de aprobación");
            }
            d.setEstado(estado);
        }
        if (body.containsKey("descripcion")) d.setDescripcion((String) body.get("descripcion"));
        if (body.containsKey("paginas")) d.setPaginas(((Number) body.get("paginas")).intValue());
        if (body.containsKey("tamano")) d.setTamano((String) body.get("tamano"));
        if (body.containsKey("compartido") && esAdmin()) d.setCompartido(Boolean.parseBoolean(String.valueOf(body.get("compartido"))));
        if (body.containsKey("permiso") && esAdmin()) d.setPermiso((String) body.get("permiso"));
        if (cambiaCarpeta) {
            if (nuevaCarpeta != null) {
                Carpeta carpeta = carpetaRepository.findById(nuevaCarpeta).orElseThrow(() -> new IllegalArgumentException("Carpeta no encontrada"));
                if (!puedeVerCarpeta(carpeta)) throw new org.springframework.security.access.AccessDeniedException("Sin acceso a la carpeta seleccionada");
            }
            d.setCarpetaId(nuevaCarpeta);
        }
    }

    private static String claveCategoria(String valor) {
        String clave = Normalizer.normalize(valor == null ? "" : valor.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (clave) { case "actas" -> "acta"; case "reuniones" -> "reunion"; case "proyectos" -> "proyecto"; default -> clave; };
    }

    private String categoriaCanonica(String nombre, String anterior) {
        String clave = claveCategoria(nombre);
        if (clave.isBlank()) throw new IllegalArgumentException("Selecciona un tipo documental.");
        Optional<String> conocida = categorias().stream().filter(categoria -> claveCategoria(categoria).equals(clave)).findFirst();
        if (conocida.isPresent()) return conocida.get();
        if (clave.equals(claveCategoria(anterior))) return anterior;
        Optional<String> legada = documentoRepository.findByCategoriaIgnoreCase(nombre.trim()).stream()
            .filter(this::puedeVerDocumento).map(Documento::getCategoria).findFirst();
        if (legada.isPresent()) return legada.get().trim();
        throw new IllegalArgumentException("Selecciona una categoría del catálogo vigente.");
    }

    private Map<String, Object> toDocumentoMap(Documento d) {
        DocumentoSolicitudAprobacion pendiente = solicitudAprobacionRepository.findFirstByDocumentoIdAndEstadoOrderByFechaSolicitudDesc(d.getId(), "PENDIENTE").orElse(null);
        return toDocumentoMap(d, usuarioActual(), pendiente);
    }

    private Map<Long, DocumentoSolicitudAprobacion> solicitudesPendientes(List<Documento> documentos) {
        Map<Long, DocumentoSolicitudAprobacion> pendientes = new HashMap<>();
        if (!documentos.isEmpty()) solicitudAprobacionRepository.findByDocumentoIdInAndEstadoOrderByFechaSolicitudDesc(documentos.stream().map(Documento::getId).toList(), "PENDIENTE")
            .forEach(s -> pendientes.putIfAbsent(s.getDocumento().getId(), s));
        return pendientes;
    }

    private Map<String, Object> toDocumentoMap(Documento d, Usuario actual, DocumentoSolicitudAprobacion pendiente) {
        return toDocumentoMap(d, actual, pendiente, new DocumentoAccessService.Acceso(documentoAccessService.canView(d, actual), documentoAccessService.canModify(d, actual), documentoAccessService.canManageAccess(d, actual)));
    }

    private Map<String, Object> toDocumentoMap(Documento d, Usuario actual, DocumentoSolicitudAprobacion pendiente, DocumentoAccessService.Acceso acceso) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(d.getId()));
        m.put("nombre", d.getNombre());
        m.put("categoria", d.getCategoria());
        m.put("area", d.getArea());
        m.put("version", d.getArchivoRuta() == null || d.getArchivoRuta().isBlank() ? "Sin archivo" : d.getVersion());
        m.put("versionActualId", d.getVersionActualId());
        m.put("fecha", d.getFecha().toString());
        m.put("propietario", d.getPropietario());
        m.put("propietarioId", d.getPropietarioUsuarioId());
        m.put("clasificacion", d.getClasificacion() == null ? DocumentoClasificacion.USO_INTERNO.name() : d.getClasificacion().name());
        m.put("politicaAcceso", (d.getClasificacion() == null ? DocumentoClasificacion.USO_INTERNO : d.getClasificacion()).politica().name());
        m.put("estado", d.getEstado());
        m.put("descripcion", d.getDescripcion());
        m.put("paginas", d.getPaginas());
        m.put("tamano", d.getTamano());
        m.put("compartido", d.getCompartido());
        boolean tieneArchivo = d.getArchivoRuta() != null && !d.getArchivoRuta().isBlank();
        String descargaUrl = tieneArchivo ? "/api/documentos/" + d.getId() + "/descargar" : null;
        String vistaPreviaUrl = tieneArchivo ? "/api/documentos/" + d.getId() + "/vista-previa" : null;
        m.put("archivoRuta", descargaUrl);
        m.put("descargaUrl", descargaUrl);
        m.put("vistaPreviaUrl", vistaPreviaUrl);
        m.put("permiso", d.getPermiso());
        m.put("carpetaId", d.getCarpetaId());
        m.put("puedeEditar", acceso.editar());
        m.put("puedeDescargar", acceso.ver());
        m.put("puedeAdministrarAcceso", acceso.administrar());
        m.put("puedeEliminar", documentoAccessService.canDelete(d, actual));
        boolean documentoDeUnidad = d.getArea() != null && !d.getArea().isBlank()
            && !"Personal".equalsIgnoreCase(d.getArea().trim());
        m.put("origen", documentoDeUnidad ? "UNIDAD"
            : documentoAccessService.isOwner(d, actual) ? "PROPIO" : "COMPARTIDO");
        m.put("solicitudAprobacion", null);
        if (pendiente != null) m.put("solicitudAprobacion", solicitudMap(pendiente));
        return m;
    }

    private void validarDocumento(Documento d) {
        if (d.getNombre() == null || d.getNombre().isBlank() || d.getNombre().length() > 300) throw new IllegalArgumentException("El nombre debe contener entre 1 y 300 caracteres");
        d.setNombre(d.getNombre().trim());
        if (d.getCategoria() == null || d.getCategoria().length() > 200 || d.getArea() == null || d.getArea().length() > 200
            || d.getVersion() == null || d.getVersion().isBlank() || d.getVersion().length() > 50) throw new IllegalArgumentException("Categoría, unidad o versión inválida");
        if (d.getPaginas() == null || d.getPaginas() < 0) throw new IllegalArgumentException("El número de páginas no puede ser negativo");
        if (d.getTamano() == null || d.getTamano().length() > 50 || d.getPropietario() == null || d.getPropietario().length() > 200) throw new IllegalArgumentException("Tamaño o propietario inválido");
    }

    private boolean mismaCreacion(Documento existente, Documento nuevo) {
        return Objects.equals(existente.getNombre(), nuevo.getNombre()) && Objects.equals(existente.getDescripcion(), nuevo.getDescripcion())
            && Objects.equals(existente.getCategoria(), nuevo.getCategoria()) && Objects.equals(existente.getArea(), nuevo.getArea())
            && Objects.equals(existente.getFecha(), nuevo.getFecha() == null ? LocalDate.now() : nuevo.getFecha())
            && existente.getClasificacion().politica() == nuevo.getClasificacion().politica() && Objects.equals(existente.getCarpetaId(), nuevo.getCarpetaId());
    }

    private void registrarAudit(String accion, Long documentoId, String detalle) {
        String usuario = "sistema";
        try {
            usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {}
        AuditLog log = new AuditLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad("documento");
        log.setEntidadId(documentoId);
        log.setDetalle(detalle);
        auditLogRepository.save(log);
    }

    private void registrarAuditCarpeta(String accion, Long carpetaId, String detalle) {
        String usuario = "sistema";
        try {
            usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {}
        AuditLog log = new AuditLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad("carpeta_documento");
        log.setEntidadId(carpetaId);
        log.setDetalle(detalle);
        auditLogRepository.save(log);
    }

    private Map<String, Object> solicitudMap(DocumentoSolicitudAprobacion solicitud) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", solicitud.getId()); m.put("estado", solicitud.getEstado());
        m.put("solicitanteId", solicitud.getSolicitante().getId()); m.put("solicitante", nombreCompleto(solicitud.getSolicitante()));
        m.put("aprobadorId", solicitud.getAprobador().getId()); m.put("aprobador", nombreCompleto(solicitud.getAprobador()));
        m.put("fecha", solicitud.getFechaSolicitud().toString()); m.put("comentario", solicitud.getComentarioSolicitud());
        m.put("versionId", solicitud.getVersion() == null ? null : solicitud.getVersion().getId());
        m.put("version", solicitud.getVersion() == null ? null : solicitud.getVersion().getVersion());
        m.put("respondidoPor", solicitud.getRespondidoPor() == null ? null : nombreCompleto(solicitud.getRespondidoPor()));
        m.put("fechaRespuesta", solicitud.getFechaRespuesta() == null ? null : solicitud.getFechaRespuesta().toString());
        m.put("comentarioRespuesta", solicitud.getComentarioRespuesta());
        return m;
    }

    private Usuario usuarioActual() { return usuarioRepository.findByCorreo(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Usuario no válido")); }
    private String nombreCompleto(Usuario usuario) { return ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " " + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim(); }
    private void notificarEliminacion(Documento documento, Usuario ejecutor) {
        Map<Long, Usuario> destinatarios = new LinkedHashMap<>();
        LocalDateTime ahora = LocalDateTime.now();
        documentoUsuarioAccesoRepository.findByDocumentoIdOrderByCreadoEnAsc(documento.getId()).stream()
            .filter(acceso -> acceso.getExpiraEn() == null || acceso.getExpiraEn().isAfter(ahora))
            .map(DocumentoUsuarioAcceso::getUsuario)
            .filter(Objects::nonNull)
            .filter(usuario -> usuario.getId() != null)
            .forEach(usuario -> destinatarios.putIfAbsent(usuario.getId(), usuario));

        if (documento.getCarpetaId() != null) {
            carpetaRepository.findById(documento.getCarpetaId())
                .filter(carpeta -> "COMPARTIDA".equalsIgnoreCase(carpeta.getTipo()))
                .ifPresent(carpeta -> carpetaUsuarioRepository.findByCarpetaId(carpeta.getId()).stream()
                    .map(CarpetaUsuario::getUsuario)
                    .filter(Objects::nonNull)
                    .filter(usuario -> usuario.getId() != null)
                    .forEach(usuario -> destinatarios.putIfAbsent(usuario.getId(), usuario)));
        }

        if (documento.getPropietarioUsuarioId() != null) destinatarios.remove(documento.getPropietarioUsuarioId());
        if (ejecutor != null && ejecutor.getId() != null) destinatarios.remove(ejecutor.getId());
        if (destinatarios.isEmpty()) return;

        String propietario = Objects.toString(documento.getPropietario(), "").trim();
        if (propietario.isBlank() && documento.getPropietarioUsuarioId() != null) {
            propietario = usuarioRepository.findById(documento.getPropietarioUsuarioId())
                .map(this::nombreCompleto).orElse("");
        }
        if (propietario.isBlank() && documentoAccessService.isOwner(documento, ejecutor)) {
            propietario = nombreCompleto(ejecutor);
        }
        String mensaje = propietario.isBlank()
            ? "El propietario eliminó el documento \"" + documento.getNombre() + "\"."
            : "El propietario " + propietario + " eliminó el documento \"" + documento.getNombre() + "\".";
        for (Usuario destinatario : destinatarios.values()) {
            internalNotificationService.send("documento_eliminado", destinatario, "Documento eliminado", mensaje);
        }
    }
    private void crearNotificacion(Usuario usuario, String titulo, String mensaje) { internalNotificationService.send(titulo.contains("rechazado") ? "solicitud_rechazada" : "solicitud_aprobada", usuario, titulo, mensaje); }
    private boolean esAdmin() { return usuarioActual().getRoles().stream().anyMatch(r ->
        Boolean.TRUE.equals(r.getActivo()) && com.sigaa.service.InstitutionalRoles.operational(r.getNombre())); }
    private boolean puedeVerCarpeta(Carpeta carpeta) { return documentoAccessService.canAccessFolder(carpeta, usuarioActual()); }
    private boolean puedeVerDocumento(Documento documento) { return documentoAccessService.canView(documento, usuarioActual()); }

    private List<String> detectarCambios(Documento actual, Map<String, Object> body) {
        List<String> cambios = new ArrayList<>();
        if (body.containsKey("nombre") && !Objects.equals(actual.getNombre(), body.get("nombre")))
            cambios.add("nombre");
        if (body.containsKey("categoria") && !Objects.equals(actual.getCategoria(), body.get("categoria")))
            cambios.add("categoria");
        if (body.containsKey("area") && !Objects.equals(actual.getArea(), body.get("area")))
            cambios.add("area");
        if (body.containsKey("estado") && !Objects.equals(actual.getEstado(), body.get("estado")))
            cambios.add("estado");
        if (body.containsKey("descripcion") && !Objects.equals(actual.getDescripcion(), body.get("descripcion")))
            cambios.add("descripcion");
        if (body.containsKey("propietario") && !Objects.equals(actual.getPropietario(), body.get("propietario")))
            cambios.add("propietario");
        if (body.containsKey("clasificacion") && actual.getClasificacion().politica() != DocumentoClasificacion.from(body.get("clasificacion")).politica())
            cambios.add("clasificación");
        if (body.containsKey("carpetaId") && !Objects.equals(actual.getCarpetaId(), body.get("carpetaId") instanceof Number id ? id.longValue() : null)) cambios.add("carpeta");
        return cambios;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
