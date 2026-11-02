package com.sigaa.controller;

import com.sigaa.dto.NotaCreateRequest;
import com.sigaa.dto.NotaAnulacionRequest;
import com.sigaa.dto.NotaPdfEliminacionRequest;
import com.sigaa.dto.NotaNumerosCargaRequest;
import com.sigaa.dto.NotaRelacionRequest;
import com.sigaa.dto.NotaUpdateRequest;
import com.sigaa.dto.NotaVistaPreviaRequest;
import com.sigaa.dto.OrganizationDepartmentResponse;
import com.sigaa.dto.NotaSeguimientoRequest;
import com.sigaa.model.Nota;
import com.sigaa.model.NotaHistorial;
import com.sigaa.model.NotaNumero;
import com.sigaa.model.NotaPdf;
import com.sigaa.model.NotaRelacion;
import com.sigaa.model.Organization;
import com.sigaa.model.Usuario;
import com.sigaa.model.NotaSeguimientoEstado;
import com.sigaa.repository.NotaHistorialRepository;
import com.sigaa.repository.OrganizationRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.NotaDocumentoService;
import com.sigaa.service.NotaNumeroService;
import com.sigaa.service.NotaRelacionService;
import com.sigaa.service.NotaService;
import com.sigaa.service.CorrespondenciaAccessService;
import com.sigaa.service.NotaTrazabilidadService;
import com.sigaa.service.NotaUsuarioActualService;
import com.sigaa.service.NotaWordService;
import com.sigaa.service.NotaRenderService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/notas")
public class NotaController {
    private static final ZoneId PANAMA = ZoneId.of("America/Panama");
    private static final DateTimeFormatter FECHA_NOTA = DateTimeFormatter.ofPattern(
        "d 'de' MMMM 'del' yyyy", Locale.forLanguageTag("es-PA"));
    private final NotaService notaService;
    private final CorrespondenciaAccessService acceso;
    private final NotaNumeroService numeroService;
    private final NotaDocumentoService documentoService;
    private final NotaRelacionService relacionService;
    private final NotaUsuarioActualService usuarioActualService;
    private final NotaHistorialRepository historialRepository;
    private final NotaTrazabilidadService trazabilidad;
    private final OrganizationRepository organizationRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotaWordService wordService;
    private final NotaRenderService renderService;

    public NotaController(NotaService notaService, NotaNumeroService numeroService,
                          NotaDocumentoService documentoService, NotaRelacionService relacionService,
                          NotaUsuarioActualService usuarioActualService,
                          NotaHistorialRepository historialRepository, NotaTrazabilidadService trazabilidad,
                          OrganizationRepository organizationRepository, UsuarioRepository usuarioRepository,
                          NotaWordService wordService, CorrespondenciaAccessService acceso, NotaRenderService renderService) {
        this.notaService = notaService; this.acceso = acceso;
        this.numeroService = numeroService;
        this.documentoService = documentoService;
        this.relacionService = relacionService;
        this.usuarioActualService = usuarioActualService;
        this.historialRepository = historialRepository;
        this.trazabilidad = trazabilidad;
        this.organizationRepository = organizationRepository;
        this.usuarioRepository = usuarioRepository;
        this.wordService = wordService;
        this.renderService = renderService;
    }

    @GetMapping("/contexto-borrador")
    @PreAuthorize("hasAnyAuthority('Notas:Crear','Notas:Editar','Notas:Ver')")
    @Transactional(readOnly = true)
    public Map<String, Object> contextoBorrador() {
        LocalDate fecha = LocalDate.now(PANAMA);
        List<Usuario> directoras = usuarioRepository.findActiveDirectors();
        Map<String, Object> result = new HashMap<>();
        result.put("fechaOficial", fecha.toString());
        result.put("fechaOficialTexto", fechaNotaTexto(fecha));
        result.put("directora", directoras.size() == 1 ? usuarioProfesionalMap(directoras.get(0)) : null);
        result.put("directoraDisponible", directoras.size() == 1);
        result.put("advertenciaDirectora", directoras.isEmpty()
            ? "No existe un Director o Directora activo configurado en el sistema. Podrás guardar el borrador, pero no oficializarlo."
            : directoras.size() > 1
                ? "Existe más de un Director o Directora activo. Debe quedar una sola persona antes de oficializar la nota."
                : "");
        return result;
    }

    @GetMapping("/instituciones")
    @PreAuthorize("hasAnyAuthority('Notas:Crear','Notas:Editar','Notas:Ver')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarInstituciones() {
        return organizationRepository.findAllByActivoTrueOrderByNombreAsc().stream()
            .map(this::institucionMap).toList();
    }

    @GetMapping("/instituciones/{institucionId}/departamentos")
    @PreAuthorize("hasAnyAuthority('Notas:Crear','Notas:Editar','Notas:Ver')")
    public List<OrganizationDepartmentResponse> listarDepartamentos(@PathVariable Long institucionId) {
        return notaService.departamentosActivos(institucionId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('Notas:Ver')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        Usuario actual = usuarioActualService.actual();
        return notaService.listar().stream().filter(nota -> acceso.puedeVer(nota, actual)).map(nota -> notaMap(nota, false)).toList();
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAuthority('Notas:Ver')")
    @Transactional(readOnly = true)
    public Map<String, Object> buscar(@RequestParam(defaultValue = "") String query,
                                      @RequestParam(required = false) Long excluirId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Usuario actual = usuarioActualService.actual();
        Page<Nota> resultado = notaService.buscarAutorizadas(query, excluirId, page, size, actual.getId(), acceso.administrador(actual));
        return Map.of(
            "content", resultado.getContent().stream().map(this::notaBusquedaMap).toList(),
            "page", resultado.getNumber(), "size", resultado.getSize(),
            "totalElements", resultado.getTotalElements(), "totalPages", resultado.getTotalPages());
    }

    @GetMapping("/{id}")
    @PreAuthorize("(hasAuthority('Notas:Ver')) and @correspondenciaAccessService.nota(#id)")
    @Transactional(readOnly = true)
    public Map<String, Object> obtener(@PathVariable Long id) {
        return notaMap(notaService.obtener(id), true);
    }

    @GetMapping("/numeros")
    @PreAuthorize("hasAuthority('Notas:Ver')")
    @Transactional
    public List<Map<String, Object>> listarNumeros() {
        Usuario actual = usuarioActualService.actual();
        return numeroService.listar().stream().map(numero -> numeroMap(numero, actual)).toList();
    }

    @PostMapping("/numeros/cargar")
    @PreAuthorize("hasAuthority('Notas:Gestionar números')")
    @Transactional
    public ResponseEntity<Map<String, Object>> cargarNumeros(@Valid @RequestBody NotaNumerosCargaRequest request) {
        return ResponseEntity.status(201).body(Map.of("cargados", numeroService.cargar(request.getNumeros())));
    }

    @PostMapping("/numeros/{id}/reservar")
    @PreAuthorize("hasAuthority('Notas:Reservar número')")
    @Transactional
    public Map<String, Object> reservarNumero(@PathVariable Long id) {
        Usuario actual = usuarioActualService.actual();
        return numeroMap(numeroService.reservar(id, actual), actual);
    }

    @PostMapping("/numeros/{id}/renovar")
    @PreAuthorize("hasAuthority('Notas:Reservar número')")
    @Transactional
    public Map<String, Object> renovarNumero(@PathVariable Long id) {
        Usuario actual = usuarioActualService.actual();
        return numeroMap(numeroService.renovar(id, actual), actual);
    }

    @DeleteMapping("/numeros/{id}/reserva")
    @PreAuthorize("hasAnyAuthority('Notas:Reservar número','Notas:Gestionar números')")
    public ResponseEntity<Void> liberarNumero(@PathVariable Long id) {
        Usuario actual = usuarioActualService.actual();
        numeroService.liberar(id, actual, usuarioActualService.tieneAutoridad("Notas:Gestionar números"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('Notas:Crear')")
    @Transactional
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestPart("datos") NotaCreateRequest request,
                                                      @RequestPart(value = "pdf", required = false) MultipartFile pdf) {
        Usuario actual = usuarioActualService.actual();
        if (pdf != null && !pdf.isEmpty() && !usuarioActualService.tieneAutoridad("Notas:Adjuntar PDF")) {
            throw new AccessDeniedException("No tiene permiso para adjuntar PDF.");
        }
        request.getRelaciones().forEach(relacion -> acceso.exigirNota(relacion.getNotaRelacionadaId()));
        Nota guardada = notaService.crear(request, pdf, actual, usuarioActualService.tieneAutoridad("Notas:Relacionar"));
        return ResponseEntity.status(201).body(notaMap(guardada, true));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("(hasAuthority('Notas:Editar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public Map<String, Object> actualizar(@PathVariable Long id,
                                          @Valid @RequestPart("datos") NotaUpdateRequest request,
                                          @RequestPart(value = "pdf", required = false) MultipartFile pdf) {
        Usuario actual = usuarioActualService.actual();
        if (request.getDescripcion() != null && !request.getDescripcion().isBlank()
                && !usuarioActualService.tieneAutoridad("Notas:Dar seguimiento")) {
            throw new AccessDeniedException("No tiene permiso para agregar comentarios de seguimiento.");
        }
        Nota guardada = notaService.actualizar(id, request, actual);
        if (pdf != null && !pdf.isEmpty()) {
            if (!usuarioActualService.tieneAutoridad("Notas:Adjuntar PDF")) throw new AccessDeniedException("No tiene permiso para adjuntar PDF.");
            documentoService.adjuntar(id, pdf, actual);
        }
        return notaMap(guardada, true);
    }

    @PostMapping("/{id}/firmar")
    @PreAuthorize("(hasAuthority('Notas:Firmar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public Map<String, Object> firmar(@PathVariable Long id) {
        return notaMap(notaService.firmar(id, usuarioActualService.actual()), true);
    }

    @PostMapping("/{id}/seguimientos")
    @PreAuthorize("hasAuthority('Notas:Ver') and hasAnyAuthority('Notas:Dar seguimiento','Notas:Cambiar seguimiento','Notas:Firmar') and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public Map<String, Object> seguimiento(@PathVariable Long id, @Valid @RequestBody NotaSeguimientoRequest request) {
        return notaMap(notaService.seguimiento(id, request, usuarioActualService.actual(),
            usuarioActualService.tieneAutoridad("Notas:Dar seguimiento"), estadosSeguimientoPermitidos()), true);
    }

    private Set<NotaSeguimientoEstado> estadosSeguimientoPermitidos() {
        return Arrays.stream(NotaSeguimientoEstado.values())
            .filter(estado -> usuarioActualService.tieneAutoridad(estado.autoridad())).collect(Collectors.toSet());
    }

    @PostMapping("/{id}/oficializar")
    @PreAuthorize("(hasAuthority('Notas:Firmar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public Map<String, Object> oficializar(@PathVariable Long id) {
        return notaMap(notaService.oficializar(id, usuarioActualService.actual()), true);
    }

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("(hasAuthority('Notas:Cerrar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public Map<String, Object> cerrar(@PathVariable Long id) {
        return notaMap(notaService.cerrar(id, usuarioActualService.actual()), true);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("(hasAuthority('Notas:Anular')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public Map<String, Object> anular(@PathVariable Long id, @Valid @RequestBody NotaAnulacionRequest request) {
        return notaMap(notaService.anular(id, request.getMotivo(), usuarioActualService.actual()), true);
    }

    @PostMapping(value = "/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("(hasAuthority('Notas:Adjuntar PDF')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public ResponseEntity<Map<String, Object>> adjuntarPdf(@PathVariable Long id, @RequestPart("pdf") MultipartFile pdf) {
        documentoService.adjuntar(id, pdf, usuarioActualService.actual());
        return ResponseEntity.status(201).body(notaMap(notaService.obtener(id), true));
    }

    @GetMapping("/{id}/pdfs")
    @PreAuthorize("(hasAuthority('Notas:Descargar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPdfs(@PathVariable Long id) {
        return documentoService.listar(id).stream().map(this::pdfMap).toList();
    }

    @GetMapping("/{id}/word")
    @PreAuthorize("(hasAuthority('Notas:Descargar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> descargarWord(@PathVariable Long id) {
        Nota nota = notaService.obtener(id);
        byte[] documento = wordService.generar(nota);
        String nombre = NotaWordService.nombreArchivo(nota, "docx");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .contentLength(documento.length)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(documento);
    }

    @GetMapping("/listado")
    @PreAuthorize("hasAuthority('Notas:Ver')")
    @Transactional(readOnly = true)
    public Map<String, Object> listado(@RequestParam(defaultValue = "") String query,
                                      @RequestParam(defaultValue = "") String numero,
                                      @RequestParam(defaultValue = "") String destinatario,
                                      @RequestParam(defaultValue = "") String estado,
                                      @RequestParam(required = false) Integer anio,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
                                      @RequestParam(defaultValue = "") String responsable,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        if (anio != null && (anio < 1 || anio > 9999)) throw new IllegalArgumentException("El año debe estar entre 1 y 9999.");
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        estado = filtroListado(estado).toUpperCase(Locale.ROOT);
        if (!estado.isBlank() && !List.of("BORRADOR", "EN_SEGUIMIENTO", "FIRMADA", "CERRADA", "ANULADA").contains(estado)) {
            throw new IllegalArgumentException("El estado de nota no es válido.");
        }
        Usuario actual = usuarioActualService.actual();
        NotaService.Listado resultado = notaService.listado(filtroListado(query),
            filtroListado(numero).replaceFirst("(?i)^nota\\.\\s*n[°º]\\s*", ""), filtroListado(destinatario), estado,
            anio == null ? null : LocalDate.of(anio, 1, 1), anio == null ? null : LocalDate.of(anio, 12, 31),
            fechaDesde, fechaHasta, filtroListado(responsable), page, size, actual.getId(), acceso.administrador(actual));
        return Map.of("content", resultado.notas().stream().map(nota -> notaMap(nota, false)).toList(),
            "page", resultado.page(), "size", resultado.size(), "totalElements", resultado.total(),
            "totalPages", resultado.totalPages(), "resumen", resultado.resumen());
    }

    private String filtroListado(String value) {
        String texto = value == null ? "" : value.trim();
        if (texto.length() > 200) throw new IllegalArgumentException("Los filtros de texto admiten hasta 200 caracteres.");
        return texto;
    }

    @PostMapping("/vista-previa")
    @PreAuthorize("hasAnyAuthority('Notas:Crear','Notas:Editar','Notas:Ver')")
    @Transactional(readOnly = true)
    public ResponseEntity<NotaRenderService.VistaPrevia> vistaPrevia(@Valid @RequestBody NotaVistaPreviaRequest request) {
        if (request.notaId() != null) acceso.exigirNota(request.notaId());
        Nota nota = notaService.prepararVistaPrevia(request, usuarioActualService.actual());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(renderService.vistaPrevia(wordService.generarVistaPrevia(nota), NotaWordService.nombreArchivo(nota, "docx"), NotaService.ANULADA.equals(nota.getEstado())));
    }

    @GetMapping("/{id}/documento.pdf")
    @PreAuthorize("(hasAuthority('Notas:Descargar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> descargarDocumentoPdf(@PathVariable Long id) {
        Nota nota = notaService.obtener(id);
        byte[] pdf = renderService.pdf(wordService.generar(nota));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(pdf.length)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(NotaWordService.nombreArchivo(nota, "pdf"), StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(pdf);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("(hasAuthority('Notas:Descargar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> descargarPdf(@PathVariable Long id) { return respuestaPdf(documentoService.vigente(id)); }

    @GetMapping("/{id}/pdfs/{pdfId}")
    @PreAuthorize("(hasAuthority('Notas:Descargar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> descargarVersion(@PathVariable Long id, @PathVariable Long pdfId) {
        return respuestaPdf(documentoService.obtener(id, pdfId));
    }

    @PostMapping("/{id}/pdfs/{pdfId}/eliminar")
    @PreAuthorize("(hasAuthority('Notas:Eliminar PDF')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public Map<String, Object> eliminarPdf(@PathVariable Long id, @PathVariable Long pdfId,
                                           @Valid @RequestBody NotaPdfEliminacionRequest request) {
        documentoService.eliminar(id, pdfId, request.getMotivo(), usuarioActualService.actual());
        return notaMap(notaService.obtener(id), true);
    }

    @PostMapping("/{id}/relaciones")
    @PreAuthorize("(hasAuthority('Notas:Relacionar')) and @correspondenciaAccessService.nota(#id)")
    @Transactional
    public ResponseEntity<Map<String, Object>> crearRelacion(@PathVariable Long id, @Valid @RequestBody NotaRelacionRequest request) {
        acceso.exigirNota(request.getNotaRelacionadaId());
        relacionService.crear(id, request, usuarioActualService.actual());
        return ResponseEntity.status(201).body(notaMap(notaService.obtener(id), true));
    }

    @DeleteMapping("/{id}/relaciones/{relacionId}")
    @PreAuthorize("(hasAuthority('Notas:Relacionar')) and @correspondenciaAccessService.nota(#id)")
    public ResponseEntity<Void> eliminarRelacion(@PathVariable Long id, @PathVariable Long relacionId) {
        NotaRelacion relacion = relacionService.listar(id).stream().filter(item -> item.getId().equals(relacionId))
            .findFirst().orElseThrow(() -> new java.util.NoSuchElementException("La relación no existe."));
        acceso.exigirNota(relacion.getNotaOrigen().getId());
        acceso.exigirNota(relacion.getNotaDestino().getId());
        relacionService.eliminar(id, relacionId, usuarioActualService.actual());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Resource> respuestaPdf(NotaPdf pdf) {
        Path path = documentoService.archivo(pdf);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(pdf.getTamanoBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.getNombreOriginal(), StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(resource);
    }

    private Map<String, Object> notaMap(Nota nota, boolean detalle) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(nota.getId()));
        result.put("numeroId", String.valueOf(nota.getNumero().getId()));
        result.put("numero", NotaWordService.numeroInstitucional(nota));
        result.put("documentoNombre", NotaWordService.nombreArchivo(nota, "docx"));
        result.put("asunto", nota.getAsunto());
        result.put("destino", nota.getDestino());
        result.put("fechaOficial", nota.getFechaOficial() == null ? null : nota.getFechaOficial().toString());
        result.put("fechaOficialTexto", fechaNotaTexto(nota.getFechaOficial()));
        result.put("destinatarioNombre", nota.getDestinatarioNombre());
        result.put("destinatarioCargo", nota.getDestinatarioCargo());
        Map<String, Object> institucion = institucionMap(nota.getInstitucion());
        if (institucion != null) institucion.put("nombre", nota.getInstitucionNombreGuardado());
        result.put("institucion", institucion);
        result.put("departamento", nota.getDepartamento() == null ? null : Map.of(
            "id", String.valueOf(nota.getDepartamento().getId()),
            "nombre", nota.getDepartamentoNombre() == null ? "" : nota.getDepartamentoNombre()));
        result.put("contenido", nota.getContenido());
        result.put("descripcion", nota.getDescripcion());
        result.put("sensitiva", nota.isSensitiva());
        result.put("creadoPor", usuarioMap(nota.getCreadoPor()));
        result.put("seguimientoUsuario", usuarioMap(nota.getSeguimientoUsuario()));
        result.put("estado", nota.getEstado());
        result.put("tienePdf", nota.getPdfRuta() != null && !nota.getPdfRuta().isBlank());
        result.put("pdfNombre", nota.getPdfNombre());
        result.put("pdfTamanoBytes", nota.getPdfTamanoBytes());
        result.put("firmadaPor", usuarioMap(nota.getFirmadaPor()));
        result.put("firmadaAt", fecha(nota.getFirmadaAt()));
        result.put("cerradaPor", usuarioMap(nota.getCerradaPor()));
        result.put("cerradaAt", fecha(nota.getCerradaAt()));
        result.put("oficializadaPor", usuarioMap(nota.getOficializadaPor()));
        result.put("oficializadaAt", fecha(nota.getOficializadaAt()));
        result.put("firmante", nota.getFirmante() == null ? null : usuarioProfesionalMap(nota.getFirmante()));
        result.put("firmanteNombre", nota.getFirmanteNombre());
        result.put("firmanteCargo", nota.getFirmanteCargo());
        result.put("firmanteInstitucion", nota.getFirmanteInstitucion());
        result.put("anuladaPor", usuarioMap(nota.getAnuladaPor()));
        result.put("anuladaAt", fecha(nota.getAnuladaAt()));
        result.put("anulacionMotivo", nota.getAnulacionMotivo());
        result.put("createdAt", fecha(nota.getCreatedAt()));
        result.put("updatedAt", fecha(nota.getUpdatedAt()));
        if (detalle) {
            List<NotaHistorial> historial = historialRepository.findByNotaIdOrderByCreatedAtAscIdAsc(nota.getId());
            result.put("puedeAdjuntarPdf", usuarioActualService.tieneAutoridad("Notas:Adjuntar PDF")
                && documentoService.puedeAdjuntar(nota, historial));
            result.put("historial", historial.stream().map(this::historialMap).toList());
            result.put("seguimiento", seguimientoMap(nota, historial));
            result.put("pdfs", usuarioActualService.tieneAutoridad("Notas:Descargar")
                ? documentoService.listar(nota.getId()).stream().map(this::pdfMap).toList() : List.of());
            result.put("relaciones", relacionService.listar(nota.getId()).stream().filter(relacion -> acceso.puedeVer(relacion.getNotaOrigen(), usuarioActualService.actual()) && acceso.puedeVer(relacion.getNotaDestino(), usuarioActualService.actual())).map(relacion -> relacionMap(relacion, nota.getId())).toList());
        } else {
            result.put("historial", List.of());
            result.put("pdfs", List.of());
            result.put("relaciones", List.of());
        }
        return result;
    }

    private Map<String, Object> notaBusquedaMap(Nota nota) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(nota.getId())); result.put("numero", NotaWordService.numeroInstitucional(nota));
        result.put("asunto", nota.getAsunto()); result.put("estado", nota.getEstado()); result.put("sensitiva", nota.isSensitiva());
        result.put("creadoPor", usuarioMap(nota.getCreadoPor())); result.put("seguimientoUsuario", usuarioMap(nota.getSeguimientoUsuario()));
        return result;
    }

    private Map<String, Object> numeroMap(NotaNumero numero, Usuario actual) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(numero.getId()));
        result.put("numero", numero.getConsecutivo() == null ? numero.getNumero() : NotaNumeroService.formatear(numero.getConsecutivo(), numero.getAnio()));
        result.put("estado", numero.getEstado());
        Nota nota = numero.getNota();
        if (nota != null && !acceso.puedeVer(nota, actual)) nota = null;
        result.put("notaId", nota == null ? null : String.valueOf(nota.getId()));
        result.put("tomadoPor", nota == null ? null : usuarioMap(nota.getCreadoPor())); result.put("tomadoAt", nota == null ? null : fecha(nota.getCreatedAt()));
        result.put("reservadoPor", usuarioMap(numero.getReservadoPor())); result.put("reservadoDesde", fecha(numero.getReservadoDesde()));
        result.put("reservadoHasta", fecha(numero.getReservadoHasta()));
        result.put("reservaPropia", numero.getReservadoPor() != null && numero.getReservadoPor().getId().equals(actual.getId()));
        return result;
    }

    private Map<String, Object> pdfMap(NotaPdf pdf) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(pdf.getId())); result.put("numeroVersion", pdf.getNumeroVersion());
        result.put("nombreOriginal", pdf.getNombreOriginal()); result.put("tipoMime", pdf.getTipoMime()); result.put("tamanoBytes", pdf.getTamanoBytes());
        result.put("usuario", usuarioMap(pdf.getUsuario())); result.put("createdAt", fecha(pdf.getCreatedAt())); result.put("vigente", pdf.getVigente());
        return result;
    }

    private Map<String, Object> relacionMap(NotaRelacion relacion, Long notaId) {
        boolean saliente = relacion.getNotaOrigen().getId().equals(notaId);
        Nota otra = saliente ? relacion.getNotaDestino() : relacion.getNotaOrigen();
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(relacion.getId())); result.put("tipo", relacion.getTipo()); result.put("direccion", saliente ? "SALIENTE" : "ENTRANTE");
        result.put("nota", notaBusquedaMap(otra)); result.put("creadoPor", usuarioMap(relacion.getCreadoPor())); result.put("createdAt", fecha(relacion.getCreatedAt()));
        return result;
    }

    private Map<String, Object> historialMap(NotaHistorial historial) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(historial.getId())); result.put("usuario", usuarioMap(historial.getUsuario())); result.put("accion", historial.getAccion());
        result.put("accionTexto", NotaSeguimientoEstado.porAccion(historial.getAccion()).map(NotaSeguimientoEstado::texto)
            .orElseGet(() -> switch (historial.getAccion()) {
                case "FIRMAR" -> "Firma de la directora o director";
                case "SEGUIMIENTO", "SEGUIMIENTO_INICIAL" -> "Comentario de seguimiento";
                case "CAMBIAR_RESPONSABLE" -> "Cambio de responsable";
                default -> historial.getAccion().replace('_', ' ');
            }));
        result.put("estadoAnterior", historial.getEstadoAnterior()); result.put("estadoNuevo", historial.getEstadoNuevo()); result.put("detalle", historial.getAccion().contains("RELACION") && !acceso.administrador(usuarioActualService.actual()) ? "Se actualizó una relación entre correspondencias." : historial.getDetalle());
        result.put("createdAt", fecha(historial.getCreatedAt())); return result;
    }

    private Map<String, Object> seguimientoMap(Nota nota, List<NotaHistorial> historial) {
        var actual = trazabilidad.seguimientoActual(nota, historial);
        boolean abierta = NotaService.FIRMADA.equals(nota.getEstado());
        Map<String, Object> result = new HashMap<>();
        result.put("estado", actual.estado() == null ? null : actual.estado().name());
        result.put("estadoTexto", actual.estado() == null ? "Sin seguimiento registrado" : actual.estado().texto());
        result.put("usuario", actual.evento() == null ? usuarioMap(nota.getFirmadaPor()) : usuarioMap(actual.evento().getUsuario()));
        result.put("createdAt", actual.evento() == null ? fecha(nota.getFirmadaAt()) : fecha(actual.evento().getCreatedAt()));
        result.put("puedeComentar", abierta && usuarioActualService.tieneAutoridad("Notas:Dar seguimiento"));
        result.put("opciones", abierta ? Arrays.stream(NotaSeguimientoEstado.values())
            .filter(estado -> usuarioActualService.tieneAutoridad(estado.autoridad()))
            .map(estado -> Map.of("estado", estado.name(), "texto", estado.texto())).toList() : List.of());
        return result;
    }

    private Map<String, Object> usuarioMap(Usuario usuario) {
        if (usuario == null) return null;
        return Map.of("id", String.valueOf(usuario.getId()), "nombre", nombreCompleto(usuario));
    }

    private Map<String, Object> usuarioProfesionalMap(Usuario usuario) {
        if (usuario == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(usuario.getId()));
        result.put("nombre", nombreCompleto(usuario));
        result.put("cargo", usuario.getCargo() == null ? "" : usuario.getCargo());
        result.put("institucion", usuario.getInstitucion() == null ? "" : usuario.getInstitucion());
        return result;
    }

    private Map<String, Object> institucionMap(Organization institucion) {
        if (institucion == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(institucion.getId()));
        result.put("nombre", institucion.getNombre());
        result.put("siglas", institucion.getSiglas() == null ? "" : institucion.getSiglas());
        return result;
    }

    private String fecha(LocalDateTime value) { return value == null ? null : value.toString(); }
    private String fechaNotaTexto(LocalDate value) { return value == null ? null : "Panamá, " + FECHA_NOTA.format(value); }
    private String nombreCompleto(Usuario usuario) {
        return String.join(" ", usuario.getNombre() == null ? "" : usuario.getNombre(), usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(), usuario.getApellido() == null ? "" : usuario.getApellido())
            .trim().replaceAll("\\s+", " ");
    }
}
