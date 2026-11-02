package com.sigaa.controller;

import com.sigaa.dto.*;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import com.sigaa.service.MemoWordService;
import com.sigaa.service.NotaRenderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memos")
public class MemoController {
    private final MemoService memoService;
    private final CorrespondenciaAccessService acceso;
    private final MemoNumeroService numeroService;
    private final MemoNumeroRepository numeroRepository;
    private final MemoHistorialRepository historialRepository;
    private final NotaUsuarioActualService usuarioActualService;
    private final MemoTrazabilidadService trazabilidad;
    private final MemoWordService wordService;
    private final NotaRenderService renderService;

    public MemoController(MemoService memoService, MemoNumeroService numeroService, MemoNumeroRepository numeroRepository,
                          MemoHistorialRepository historialRepository, NotaUsuarioActualService usuarioActualService,
                          MemoTrazabilidadService trazabilidad, CorrespondenciaAccessService acceso,
                          MemoWordService wordService, NotaRenderService renderService) {
        this.memoService = memoService; this.acceso = acceso; this.numeroService = numeroService; this.numeroRepository = numeroRepository;
        this.historialRepository = historialRepository; this.usuarioActualService = usuarioActualService; this.trazabilidad = trazabilidad;
        this.wordService = wordService; this.renderService = renderService;
    }

    @GetMapping("/{id}/vista-previa")
    @PreAuthorize("hasAuthority('Memos:Ver') and @correspondenciaAccessService.memo(#id)")
    @Transactional(readOnly = true)
    public ResponseEntity<NotaRenderService.VistaPrevia> vistaPrevia(@PathVariable Long id) {
        Memo memo = memoService.obtener(id);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(renderService.vistaPrevia(wordService.generarVistaPrevia(memo), MemoWordService.nombreArchivo(memo, "docx"),
                MemoService.ANULADO.equals(memo.getEstado()) ? "ANULADO" : ""));
    }

    @GetMapping("/{id}/documento.pdf")
    @PreAuthorize("hasAuthority('Memos:Ver') and @correspondenciaAccessService.memo(#id)")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long id) {
        Memo memo = memoService.obtener(id);
        return documento(renderService.pdf(wordService.generar(memo)), "application/pdf", MemoWordService.nombreArchivo(memo, "pdf"));
    }

    @GetMapping("/{id}/word")
    @PreAuthorize("hasAuthority('Memos:Ver') and @correspondenciaAccessService.memo(#id)")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> descargarWord(@PathVariable Long id) {
        Memo memo = memoService.obtener(id);
        return documento(wordService.generar(memo), "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            MemoWordService.nombreArchivo(memo, "docx"));
    }

    private ResponseEntity<byte[]> documento(byte[] bytes, String tipo, String nombre) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(tipo)).contentLength(bytes.length)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(bytes);
    }

    @GetMapping @PreAuthorize("hasAuthority('Memos:Ver')") @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() { Usuario actual = usuarioActualService.actual(); return memoService.listar().stream().filter(memo -> acceso.puedeVer(memo, actual)).map(memo -> memoMap(memo, false)).toList(); }

    @GetMapping("/{id}") @PreAuthorize("(hasAuthority('Memos:Ver')) and @correspondenciaAccessService.memo(#id)") @Transactional(readOnly = true)
    public Map<String, Object> obtener(@PathVariable Long id) { return memoMap(memoService.obtener(id), true); }

    @GetMapping("/numeros") @PreAuthorize("hasAuthority('Memos:Ver')") @Transactional
    public List<Map<String, Object>> numeros() {
        Usuario actual = usuarioActualService.actual();
        return numeroService.listar().stream().map(numero -> numeroMap(numero, actual)).toList();
    }

    @PostMapping("/numeros/cargar") @PreAuthorize("hasAuthority('Memos:Gestionar números')") @Transactional
    public ResponseEntity<Map<String, Object>> cargarNumeros(@Valid @RequestBody MemoNumerosCargaRequest request) {
        Set<String> normalizados = new LinkedHashSet<>(); List<String> repetidos = new ArrayList<>();
        for (String value : request.getNumeros()) { String numero = normalizarNumero(value); if (!normalizados.add(numero)) repetidos.add(numero); }
        if (!repetidos.isEmpty()) throw new IllegalArgumentException("La carga contiene números repetidos: " + String.join(", ", repetidos));
        List<String> existentes = normalizados.stream().filter(numeroRepository::existsByNumeroIgnoreCase).toList();
        if (!existentes.isEmpty()) throw new IllegalArgumentException("Ya existen en el sistema: " + String.join(", ", existentes));
        List<MemoNumero> nuevos = normalizados.stream().map(value -> { MemoNumero item = new MemoNumero(); item.setNumero(value); return item; }).toList();
        numeroRepository.saveAll(nuevos); trazabilidad.audit("CARGAR_NUMEROS", null, "Se cargaron " + nuevos.size() + " números de memo.");
        return ResponseEntity.status(201).body(Map.of("cargados", nuevos.size()));
    }

    @PostMapping("/numeros/{id}/reservar") @PreAuthorize("hasAuthority('Memos:Reservar número')") @Transactional
    public Map<String, Object> reservar(@PathVariable Long id) {
        Usuario actual = usuarioActualService.actual(); return numeroMap(numeroService.reservar(id, actual), actual);
    }

    @DeleteMapping("/numeros/{id}/reserva") @PreAuthorize("hasAnyAuthority('Memos:Reservar número','Memos:Gestionar números')")
    public ResponseEntity<Void> liberar(@PathVariable Long id) {
        numeroService.liberar(id, usuarioActualService.actual(), usuarioActualService.tieneAutoridad("Memos:Gestionar números"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping @PreAuthorize("hasAuthority('Memos:Crear')") @Transactional
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody MemoCreateRequest request) {
        Memo memo = memoService.crear(request, usuarioActualService.actual());
        return ResponseEntity.status(201).body(memoMap(memo, true));
    }

    @PostMapping("/{id}/seguimientos") @PreAuthorize("(hasAuthority('Memos:Dar seguimiento')) and @correspondenciaAccessService.memo(#id)") @Transactional
    public Map<String, Object> seguimiento(@PathVariable Long id, @Valid @RequestBody MemoSeguimientoRequest request) {
        return memoMap(memoService.seguimiento(id, request.getDescripcion(), usuarioActualService.actual()), true);
    }

    @PostMapping("/{id}/completar") @PreAuthorize("(hasAuthority('Memos:Completar')) and @correspondenciaAccessService.memo(#id)") @Transactional
    public Map<String, Object> completar(@PathVariable Long id) { return memoMap(memoService.completar(id, usuarioActualService.actual()), true); }

    @PostMapping("/{id}/anular") @PreAuthorize("(hasAuthority('Memos:Anular')) and @correspondenciaAccessService.memo(#id)") @Transactional
    public Map<String, Object> anular(@PathVariable Long id, @Valid @RequestBody MemoAnulacionRequest request) {
        return memoMap(memoService.anular(id, request.getMotivo(), usuarioActualService.actual()), true);
    }

    private Map<String, Object> memoMap(Memo memo, boolean detalle) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(memo.getId())); result.put("numeroId", String.valueOf(memo.getNumero().getId()));
        result.put("numero", memo.getNumero().getNumero()); result.put("asunto", memo.getAsunto()); result.put("destino", memo.getDestino());
        result.put("descripcion", memo.getDescripcion()); result.put("creadoPor", usuarioMap(memo.getCreadoPor())); result.put("seguimientoUsuario", usuarioMap(memo.getSeguimientoUsuario()));
        result.put("estado", memo.getEstado()); result.put("completadoPor", usuarioMap(memo.getCompletadoPor())); result.put("completadoAt", fecha(memo.getCompletadoAt()));
        result.put("anuladoPor", usuarioMap(memo.getAnuladoPor())); result.put("anuladoAt", fecha(memo.getAnuladoAt())); result.put("anulacionMotivo", memo.getAnulacionMotivo());
        result.put("createdAt", fecha(memo.getCreatedAt())); result.put("updatedAt", fecha(memo.getUpdatedAt()));
        result.put("historial", detalle ? historialRepository.findByMemoIdOrderByCreatedAtAsc(memo.getId()).stream().map(this::historialMap).toList() : List.of());
        return result;
    }

    private Map<String, Object> numeroMap(MemoNumero numero, Usuario actual) {
        Map<String, Object> result = new HashMap<>(); result.put("id", String.valueOf(numero.getId())); result.put("numero", numero.getNumero()); result.put("estado", numero.getEstado());
        Memo memo = numero.getMemo();
        result.put("memoId", memo == null || !acceso.puedeVer(memo, actual) ? null : String.valueOf(memo.getId())); result.put("reservadoPor", usuarioMap(numero.getReservadoPor()));
        result.put("reservadoDesde", fecha(numero.getReservadoDesde())); result.put("reservadoHasta", fecha(numero.getReservadoHasta()));
        result.put("reservaPropia", numero.getReservadoPor() != null && numero.getReservadoPor().getId().equals(actual.getId())); return result;
    }

    private Map<String, Object> historialMap(MemoHistorial item) {
        Map<String, Object> result = new HashMap<>(); result.put("id", String.valueOf(item.getId())); result.put("usuario", usuarioMap(item.getUsuario()));
        result.put("accion", item.getAccion()); result.put("estadoAnterior", item.getEstadoAnterior()); result.put("estadoNuevo", item.getEstadoNuevo());
        result.put("detalle", item.getDetalle()); result.put("createdAt", fecha(item.getCreatedAt())); return result;
    }

    private Map<String, Object> usuarioMap(Usuario usuario) { return usuario == null ? null : Map.of("id", String.valueOf(usuario.getId()), "nombre", nombreCompleto(usuario)); }
    private String nombreCompleto(Usuario usuario) { return String.join(" ", usuario.getNombre() == null ? "" : usuario.getNombre(), usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(), usuario.getApellido() == null ? "" : usuario.getApellido()).trim().replaceAll("\\s+", " "); }
    private String normalizarNumero(String value) { String numero = value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT); if (numero.isBlank()) throw new IllegalArgumentException("Los números de memo no pueden estar vacíos."); if (numero.length() > 50) throw new IllegalArgumentException("Cada número admite hasta 50 caracteres."); return numero; }
    private String fecha(LocalDateTime value) { return value == null ? null : value.toString(); }
}
