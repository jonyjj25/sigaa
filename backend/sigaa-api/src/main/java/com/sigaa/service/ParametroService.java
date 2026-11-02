package com.sigaa.service;

import com.sigaa.dto.ParametroRequest;
import com.sigaa.dto.ParametroResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Modulo;
import com.sigaa.model.Parametro;
import com.sigaa.model.ParameterHistory;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.ModuloRepository;
import com.sigaa.repository.ParameterHistoryRepository;
import com.sigaa.repository.ParametroRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParametroService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ParametroRepository parametroRepository;
    private final ParameterHistoryRepository historyRepository;
    private final AuditLogRepository auditLogRepository;
    private final ModuloRepository moduloRepository;

    public ParametroService(ParametroRepository parametroRepository,
                           ParameterHistoryRepository historyRepository,
                           AuditLogRepository auditLogRepository,
                           ModuloRepository moduloRepository) {
        this.parametroRepository = parametroRepository;
        this.historyRepository = historyRepository;
        this.auditLogRepository = auditLogRepository;
        this.moduloRepository = moduloRepository;
    }

    public List<ParametroResponse> listarTodos() {
        return parametroRepository.findAllByOrderByGrupoAscOrdenAscClaveAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarActivos() {
        return parametroRepository.findByActivoTrueOrderByGrupoAscOrdenAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarVisibles() {
        return parametroRepository.findByVisibleTrueAndActivoTrueOrderByGrupoAscOrdenAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarPorGrupo(String grupo) {
        return parametroRepository.findByGrupoOrderByOrdenAscClaveAsc(grupo).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarPorCategoria(String categoria) {
        return parametroRepository.findByCategoriaOrderByOrdenAsc(categoria).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarPorEstado(String estado) {
        return parametroRepository.findByEstadoOrderByOrdenAsc(estado).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarPorTipo(String tipo) {
        return parametroRepository.findByTipoOrderByOrdenAsc(tipo).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarPorModulo(Long moduloId) {
        return parametroRepository.findByModuloIdOrderByOrdenAsc(moduloId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> listarRequierenReinicio() {
        return parametroRepository.findByRequiereReinicioTrueAndActivoTrue().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ParametroResponse> buscar(String termino) {
        return parametroRepository
            .findByNombreContainingIgnoreCaseOrClaveContainingIgnoreCaseOrCodigoContainingIgnoreCase(termino, termino, termino)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public ParametroResponse obtenerPorId(Long id) {
        Parametro p = parametroRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con id: " + id));
        return toResponse(p);
    }

    public ParametroResponse obtenerPorClave(String clave) {
        Parametro p = parametroRepository.findByClave(clave)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con clave: " + clave));
        return toResponse(p);
    }

    @Transactional
    public ParametroResponse crear(ParametroRequest request) {
        if (request.getClave() != null && parametroRepository.existsByClave(request.getClave())) {
            throw new IllegalArgumentException("Ya existe un parámetro con la clave: " + request.getClave());
        }
        if (request.getCodigo() != null && !request.getCodigo().isEmpty()
            && parametroRepository.existsByCodigo(request.getCodigo())) {
            throw new IllegalArgumentException("Ya existe un parámetro con el código: " + request.getCodigo());
        }

        Parametro p = new Parametro();
        applyRequest(p, request);
        if (p.getValorDefecto() == null) p.setValorDefecto(p.getValor());
        if (p.getOrden() == 0) p.setOrden(parametroRepository.findMaxOrden() + 1);
        if (p.getEstado() == null) p.setEstado("ACTIVO");
        if (p.getActivo() == null) p.setActivo(true);

        Parametro saved = parametroRepository.save(p);
        history(saved.getId(), saved.getClave(), null, saved.getValor(), "CREAR");
        logAudit("CREAR", "Parametro", saved.getId(), "Parámetro creado: " + saved.getClave());
        return toResponse(saved);
    }

    @Transactional
    public ParametroResponse actualizar(Long id, ParametroRequest request) {
        Parametro p = parametroRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con id: " + id));

        if (!p.getEditable()) {
            throw new IllegalArgumentException("Este parámetro está marcado como no editable");
        }

        if (request.getClave() != null && parametroRepository.existsByClaveAndIdNot(request.getClave(), id)) {
            throw new IllegalArgumentException("Ya existe otro parámetro con la clave: " + request.getClave());
        }
        if (request.getCodigo() != null && !request.getCodigo().isEmpty()
            && parametroRepository.existsByCodigoAndIdNot(request.getCodigo(), id)) {
            throw new IllegalArgumentException("Ya existe otro parámetro con el código: " + request.getCodigo());
        }

        String valorAnterior = p.getValor();
        applyRequest(p, request);

        Parametro saved = parametroRepository.save(p);
        if (!saved.getValor().equals(valorAnterior)) {
            history(saved.getId(), saved.getClave(), valorAnterior, saved.getValor(), "ACTUALIZAR");
        }
        logAudit("ACTUALIZAR", "Parametro", saved.getId(),
            "Parámetro actualizado: " + saved.getClave() + " [" + valorAnterior + " -> " + saved.getValor() + "]");
        return toResponse(saved);
    }

    @Transactional
    public ParametroResponse actualizarValor(Long id, String nuevoValor) {
        Parametro p = parametroRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con id: " + id));

        String valorAnterior = p.getValor();
        validateValue(p.getTipo(), nuevoValor);
        p.setValor(nuevoValor);

        Parametro saved = parametroRepository.save(p);
        if (!saved.getValor().equals(valorAnterior)) {
            history(saved.getId(), saved.getClave(), valorAnterior, saved.getValor(), "ACTUALIZAR_VALOR");
        }
        logAudit("ACTUALIZAR", "Parametro", saved.getId(),
            "Valor cambiado: " + saved.getClave() + " [" + valorAnterior + " -> " + saved.getValor() + "]");
        return toResponse(saved);
    }

    @Transactional
    public ParametroResponse toggleActivo(Long id) {
        Parametro p = parametroRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con id: " + id));
        p.setActivo(!p.getActivo());
        p.setEstado(p.getActivo() ? "ACTIVO" : "INACTIVO");
        Parametro saved = parametroRepository.save(p);
        logAudit("ACTUALIZAR", "Parametro", saved.getId(),
            "Parámetro " + (saved.getActivo() ? "activado" : "desactivado") + ": " + saved.getClave());
        return toResponse(saved);
    }

    @Transactional
    public ParametroResponse toggleVisible(Long id) {
        Parametro p = parametroRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con id: " + id));
        p.setVisible(!p.getVisible());
        Parametro saved = parametroRepository.save(p);
        return toResponse(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        Parametro p = parametroRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con id: " + id));
        history(id, p.getClave(), p.getValor(), null, "ELIMINAR");
        parametroRepository.delete(p);
        logAudit("ELIMINAR", "Parametro", id, "Parámetro eliminado: " + p.getClave());
    }

    @Transactional
    public ParametroResponse restaurarDefecto(Long id) {
        Parametro p = parametroRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Parámetro no encontrado con id: " + id));
        String valorAnterior = p.getValor();
        p.setValor(p.getValorDefecto() != null ? p.getValorDefecto() : "");
        Parametro saved = parametroRepository.save(p);
        history(saved.getId(), saved.getClave(), valorAnterior, saved.getValor(), "RESTAURAR_DEFECTO");
        logAudit("ACTUALIZAR", "Parametro", saved.getId(),
            "Parámetro restaurado a valor por defecto: " + saved.getClave());
        return toResponse(saved);
    }

    public List<ParameterHistory> obtenerHistorial(Long parametroId) {
        return historyRepository.findByParametroIdOrderByFechaDesc(parametroId);
    }

    public List<ParameterHistory> obtenerHistorialCompleto() {
        return historyRepository.findAllByOrderByFechaDesc();
    }

    public List<String> listarGrupos() {
        return parametroRepository.findDistinctGrupos();
    }

    public List<String> listarCategorias() {
        return parametroRepository.findDistinctCategorias();
    }

    public List<String> listarTipos() {
        return parametroRepository.findDistinctTipos();
    }

    public Map<String, Object> getEstadisticas() {
        long total = parametroRepository.count();
        long activos = parametroRepository.countByActivoTrue();
        List<String> grupos = listarGrupos();
        List<String> categorias = listarCategorias();
        return Map.of(
            "total", total,
            "activos", activos,
            "inactivos", total - activos,
            "grupos", grupos.size(),
            "categorias", categorias.size(),
            "grupoNombres", grupos,
            "categoriaNombres", categorias
        );
    }

    public void validateValue(String tipo, String valor) {
        if (tipo == null || valor == null) return;
        switch (tipo.toUpperCase()) {
            case "NUMERO":
                try { Long.parseLong(valor); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("El valor debe ser un número entero válido: " + valor); }
                break;
            case "DECIMAL":
                try { Double.parseDouble(valor); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("El valor debe ser un número decimal válido: " + valor); }
                break;
            case "BOOLEANO":
                if (!"true".equalsIgnoreCase(valor) && !"false".equalsIgnoreCase(valor)) {
                    throw new IllegalArgumentException("El valor debe ser 'true' o 'false': " + valor);
                }
                break;
            case "CORREO":
                if (!EMAIL_PATTERN.matcher(valor).matches()) {
                    throw new IllegalArgumentException("El valor debe ser un correo electrónico válido: " + valor);
                }
                break;
            case "URL":
                if (!URL_PATTERN.matcher(valor).matches()) {
                    throw new IllegalArgumentException("El valor debe ser una URL válida: " + valor);
                }
                break;
            case "UUID":
                if (!UUID_PATTERN.matcher(valor).matches()) {
                    throw new IllegalArgumentException("El valor debe ser un UUID válido: " + valor);
                }
                break;
            case "FECHA":
                try { LocalDateTime.parse(valor + "T00:00:00"); }
                catch (Exception e) { throw new IllegalArgumentException("El valor debe ser una fecha válida (YYYY-MM-DD): " + valor); }
                break;
            case "HORA":
                if (!valor.matches("^\\d{2}:\\d{2}(:\\d{2})?$")) {
                    throw new IllegalArgumentException("El valor debe ser una hora válida (HH:MM o HH:MM:SS): " + valor);
                }
                break;
            case "FECHA_HORA":
                try { LocalDateTime.parse(valor, FMT); }
                catch (Exception e) { throw new IllegalArgumentException("El valor debe ser una fecha/hora válida (YYYY-MM-DD HH:mm:ss): " + valor); }
                break;
            default:
                break;
        }
    }

    private void applyRequest(Parametro p, ParametroRequest request) {
        if (request.getCodigo() != null) p.setCodigo(request.getCodigo());
        if (request.getNombre() != null) p.setNombre(request.getNombre());
        if (request.getClave() != null) p.setClave(request.getClave());
        if (request.getValor() != null) p.setValor(request.getValor());
        if (request.getValorDefecto() != null) p.setValorDefecto(request.getValorDefecto());
        if (request.getTipo() != null) p.setTipo(request.getTipo());
        if (request.getDescripcion() != null) p.setDescripcion(request.getDescripcion());
        if (request.getGrupo() != null) p.setGrupo(request.getGrupo());
        if (request.getCategoria() != null) p.setCategoria(request.getCategoria());
        if (request.getEstado() != null) p.setEstado(request.getEstado());
        if (request.getActivo() != null) p.setActivo(request.getActivo());
        if (request.getVisible() != null) p.setVisible(request.getVisible());
        if (request.getEditable() != null) p.setEditable(request.getEditable());
        if (request.getRequiereReinicio() != null) p.setRequiereReinicio(request.getRequiereReinicio());
        if (request.getOrden() != null) p.setOrden(request.getOrden());
        if (request.getObservaciones() != null) p.setObservaciones(request.getObservaciones());
        if (request.getFechaVigencia() != null && !request.getFechaVigencia().isEmpty()) {
            p.setFechaVigencia(LocalDateTime.parse(request.getFechaVigencia(), FMT));
        }
        if (request.getFechaExpiracion() != null && !request.getFechaExpiracion().isEmpty()) {
            p.setFechaExpiracion(LocalDateTime.parse(request.getFechaExpiracion(), FMT));
        }
        if (request.getModuloId() != null) {
            Modulo modulo = moduloRepository.findById(request.getModuloId())
                .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado con id: " + request.getModuloId()));
            p.setModulo(modulo);
        }
    }

    private ParametroResponse toResponse(Parametro p) {
        ParametroResponse resp = new ParametroResponse();
        resp.setId(p.getId());
        resp.setCodigo(p.getCodigo());
        resp.setNombre(p.getNombre());
        resp.setClave(p.getClave());
        resp.setValor(p.getValor());
        resp.setValorDefecto(p.getValorDefecto());
        resp.setTipo(p.getTipo());
        resp.setDescripcion(p.getDescripcion());
        resp.setGrupo(p.getGrupo());
        resp.setCategoria(p.getCategoria());
        resp.setEstado(p.getEstado());
        resp.setActivo(p.getActivo());
        resp.setVisible(p.getVisible());
        resp.setEditable(p.getEditable());
        resp.setRequiereReinicio(p.getRequiereReinicio());
        resp.setModuloId(p.getModulo() != null ? p.getModulo().getId() : null);
        resp.setModuloNombre(p.getModulo() != null ? p.getModulo().getNombre() : null);
        resp.setOrden(p.getOrden());
        resp.setObservaciones(p.getObservaciones());
        resp.setFechaVigencia(p.getFechaVigencia());
        resp.setFechaExpiracion(p.getFechaExpiracion());
        resp.setCreatedAt(p.getCreatedAt());
        resp.setUpdatedAt(p.getUpdatedAt());
        return resp;
    }

    private void history(Long parametroId, String clave, String anterior, String nuevo, String accion) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String usuario = auth != null ? auth.getName() : "sistema";
            ParameterHistory h = new ParameterHistory(parametroId, clave, anterior, nuevo, usuario, accion, null);
            historyRepository.save(h);
        } catch (Exception ignored) {
        }
    }

    private void logAudit(String accion, String entidad, Long entidadId, String detalle) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String usuario = auth != null ? auth.getName() : "sistema";
            AuditLog log = new AuditLog();
            log.setUsuario(usuario);
            log.setAccion(accion);
            log.setEntidad(entidad);
            log.setEntidadId(entidadId);
            log.setDetalle(detalle);
            log.setFecha(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception ignored) {
        }
    }
}
