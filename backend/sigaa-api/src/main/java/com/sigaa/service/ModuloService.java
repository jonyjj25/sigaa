package com.sigaa.service;

import com.sigaa.dto.ModuloRequest;
import com.sigaa.dto.ModuloResponse;
import com.sigaa.dto.ModuloOrdenRequest;
import com.sigaa.dto.ModuloConfiguracionRequest;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Modulo;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.ModuloRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ModuloService {

    private final ModuloRepository moduloRepository;
    private final AuditLogRepository auditLogRepository;

    public ModuloService(ModuloRepository moduloRepository, AuditLogRepository auditLogRepository) {
        this.moduloRepository = moduloRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public List<ModuloResponse> listarTodos() {
        return moduloRepository.findAllByOrderByOrdenAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ModuloResponse> listarActivos() {
        return moduloRepository.findByActivoTrueOrderByOrdenAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ModuloResponse> listarMenu() {
        return moduloRepository.findByVisibleEnMenuTrueAndActivoTrueOrderByOrdenAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ModuloResponse> listarDashboard() {
        return moduloRepository.findByVisibleEnDashboardTrueAndActivoTrueOrderByOrdenAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<ModuloResponse> listarPorCategoria(String categoria) {
        return moduloRepository.findByCategoriaOrderByOrdenAsc(categoria).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public ModuloResponse obtenerPorId(Long id) {
        Modulo modulo = moduloRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado con id: " + id));
        return toResponse(modulo);
    }

    public ModuloResponse obtenerPorCodigo(String codigo) {
        Modulo modulo = moduloRepository.findByCodigo(codigo)
            .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado con código: " + codigo));
        return toResponse(modulo);
    }

    public List<ModuloResponse> buscar(String termino) {
        return moduloRepository.findByNombreContainingIgnoreCaseOrCodigoContainingIgnoreCase(termino, termino).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public ModuloResponse crear(ModuloRequest request) {
        validarCamposTecnicos(request, true);
        if (request.getCodigo() != null && moduloRepository.existsByCodigo(request.getCodigo())) {
            throw new IllegalArgumentException("Ya existe un módulo con el código: " + request.getCodigo());
        }

        Modulo modulo = new Modulo();
        applyRequest(modulo, request, true);
        // Los módulos instalados se agregan al final para no desplazar ni
        // duplicar el orden operativo existente.
        modulo.setOrden(moduloRepository.findMaxOrden() + 1);

        if (request.getPadreId() != null) {
            Modulo padre = moduloRepository.findById(request.getPadreId())
                .orElseThrow(() -> new NoSuchElementException("Módulo padre no encontrado con id: " + request.getPadreId()));
            modulo.setPadre(padre);
            modulo.setNivelJerarquico(padre.getNivelJerarquico() + 1);
        } else {
            modulo.setNivelJerarquico(0);
        }

        Modulo saved = moduloRepository.save(modulo);
        logAudit("CREAR", "Modulo", saved.getId(), "Módulo creado: " + saved.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public ModuloResponse actualizar(Long id, ModuloRequest request) {
        Modulo modulo = moduloRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado con id: " + id));

        validarCamposTecnicos(request, false);
        validarCampoInmutable("código", request.getCodigo(), modulo.getCodigo());
        validarCampoInmutable("ruta", request.getRuta(), modulo.getRuta());
        if (request.getOrden() != null && request.getOrden() != modulo.getOrden()) {
            throw new IllegalArgumentException("El orden solo puede modificarse mediante la acción de reordenar");
        }

        String antes = resumen(modulo);
        applyRequest(modulo, request, false);

        if (request.getPadreId() != null) {
            if (request.getPadreId().equals(id)) {
                throw new IllegalArgumentException("Un módulo no puede ser padre de sí mismo");
            }
            Modulo padre = moduloRepository.findById(request.getPadreId())
                .orElseThrow(() -> new NoSuchElementException("Módulo padre no encontrado con id: " + request.getPadreId()));
            modulo.setPadre(padre);
            modulo.setNivelJerarquico(padre.getNivelJerarquico() + 1);
        }

        Modulo saved = moduloRepository.save(modulo);
        logAudit("ACTUALIZAR", "Modulo", saved.getId(),
                "Antes: " + antes + ". Después: " + resumen(saved));
        return toResponse(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        Modulo modulo = moduloRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado con id: " + id));
        String antes = resumen(modulo);
        modulo.setActivo(false);
        modulo.setVisibleEnMenu(false);
        modulo.setVisibleEnDashboard(false);
        moduloRepository.save(modulo);
        logAudit("ARCHIVAR", "Modulo", id,
                "El módulo se conservó y fue retirado de navegación. Antes: " + antes
                        + ". Después: " + resumen(modulo));
    }

    @Transactional
    public ModuloResponse toggleActivo(Long id) {
        Modulo modulo = moduloRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado con id: " + id));
        return establecerEstado(id, !Boolean.TRUE.equals(modulo.getActivo()));
    }

    @Transactional
    public ModuloResponse establecerEstado(Long id, boolean activo) {
        Modulo modulo = moduloRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Módulo no encontrado con id: " + id));
        boolean anterior = Boolean.TRUE.equals(modulo.getActivo());
        modulo.setActivo(activo);
        Modulo saved = moduloRepository.save(modulo);
        logAudit("ACTUALIZAR", "Modulo", id,
            "Estado de " + saved.getNombre() + ": " + anterior + " -> " + activo);
        return toResponse(saved);
    }

    @Transactional
    public List<ModuloResponse> reordenar(List<ModuloOrdenRequest> items) {
        List<Modulo> modulos = moduloRepository.findAllByOrderByOrdenAsc();
        if (items == null || items.size() != modulos.size()) {
            throw new IllegalArgumentException("El reordenamiento debe incluir todos los módulos instalados");
        }

        Set<Long> idsEsperados = modulos.stream().map(Modulo::getId).collect(Collectors.toSet());
        Set<Long> idsRecibidos = items.stream().map(ModuloOrdenRequest::getId).collect(Collectors.toSet());
        Set<Integer> ordenes = items.stream().map(ModuloOrdenRequest::getOrden).collect(Collectors.toSet());
        if (!idsRecibidos.equals(idsEsperados) || idsRecibidos.size() != items.size()) {
            throw new IllegalArgumentException("La lista contiene módulos desconocidos, repetidos o incompletos");
        }
        if (ordenes.size() != items.size()
                || !ordenes.equals(java.util.stream.IntStream.rangeClosed(1, items.size())
                        .boxed().collect(Collectors.toSet()))) {
            throw new IllegalArgumentException("El orden debe ser consecutivo, único y comenzar en 1");
        }

        Map<Long, Integer> antes = modulos.stream()
                .collect(Collectors.toMap(Modulo::getId, Modulo::getOrden));
        Map<Long, Integer> nuevos = items.stream()
                .collect(Collectors.toMap(ModuloOrdenRequest::getId, ModuloOrdenRequest::getOrden));
        modulos.forEach(modulo -> modulo.setOrden(nuevos.get(modulo.getId())));
        moduloRepository.saveAll(modulos);
        logAudit("REORDENAR", "Modulo", null,
                "Orden anterior: " + antes + ". Orden nuevo: " + nuevos);
        return listarTodos();
    }

    @Transactional
    public List<ModuloResponse> guardarConfiguracion(List<ModuloConfiguracionRequest> items) {
        List<Modulo> modulos = moduloRepository.findAllByOrderByOrdenAsc();
        validarColeccionCompleta(
                modulos,
                items == null ? List.of() : items.stream().map(ModuloConfiguracionRequest::getId).toList(),
                items == null ? List.of() : items.stream().map(ModuloConfiguracionRequest::getOrden).toList());

        Map<Long, ModuloConfiguracionRequest> configuracion = items.stream()
                .collect(Collectors.toMap(ModuloConfiguracionRequest::getId, item -> item));
        String antes = modulos.stream().map(this::resumen).collect(Collectors.joining(", "));
        modulos.forEach(modulo -> {
            ModuloConfiguracionRequest item = configuracion.get(modulo.getId());
            modulo.setOrden(item.getOrden());
            modulo.setActivo(item.getActivo());
        });
        moduloRepository.saveAll(modulos);
        String despues = modulos.stream().map(this::resumen).collect(Collectors.joining(", "));
        logAudit("CONFIGURAR", "Modulo", null,
                "Configuración de módulos. Antes: [" + antes + "]. Después: [" + despues + "]");
        return listarTodos();
    }

    private void validarColeccionCompleta(List<Modulo> modulos, List<Long> ids, List<Integer> ordenesLista) {
        if (ids.size() != modulos.size() || ordenesLista.size() != modulos.size()) {
            throw new IllegalArgumentException("La configuración debe incluir todos los módulos instalados");
        }
        Set<Long> idsEsperados = modulos.stream().map(Modulo::getId).collect(Collectors.toSet());
        Set<Long> idsRecibidos = new java.util.HashSet<>(ids);
        Set<Integer> ordenes = new java.util.HashSet<>(ordenesLista);
        if (!idsRecibidos.equals(idsEsperados) || idsRecibidos.size() != ids.size()) {
            throw new IllegalArgumentException("La lista contiene módulos desconocidos, repetidos o incompletos");
        }
        Set<Integer> consecutivos = java.util.stream.IntStream.rangeClosed(1, modulos.size())
                .boxed().collect(Collectors.toSet());
        if (!ordenes.equals(consecutivos) || ordenes.size() != ordenesLista.size()) {
            throw new IllegalArgumentException("El orden debe ser consecutivo, único y comenzar en 1");
        }
    }

    public Map<String, Object> getEstadisticas() {
        long total = moduloRepository.count();
        long activos = moduloRepository.findByActivoTrueOrderByOrdenAsc().size();
        long menu = moduloRepository.findByVisibleEnMenuTrueAndActivoTrueOrderByOrdenAsc().size();
        int maxNivel = moduloRepository.findMaxNivel();
        return Map.of(
            "total", total,
            "activos", activos,
            "inactivos", total - activos,
            "visiblesMenu", menu,
            "nivelMaximo", maxNivel
        );
    }

    private void applyRequest(Modulo modulo, ModuloRequest request, boolean incluirCamposTecnicos) {
        if (request.getNombre() != null) modulo.setNombre(request.getNombre());
        if (incluirCamposTecnicos && request.getRuta() != null) modulo.setRuta(request.getRuta());
        if (request.getIcono() != null) modulo.setIcono(request.getIcono());
        if (request.getActivo() != null) modulo.setActivo(request.getActivo());
        if (incluirCamposTecnicos && request.getCodigo() != null) modulo.setCodigo(request.getCodigo());
        if (request.getDescripcion() != null) modulo.setDescripcion(request.getDescripcion());
        if (request.getCategoria() != null) modulo.setCategoria(request.getCategoria());
        if (request.getColor() != null) modulo.setColor(request.getColor());
        if (request.getRutaApi() != null) modulo.setRutaApi(request.getRutaApi());
        if (request.getTipoModulo() != null) modulo.setTipoModulo(request.getTipoModulo());
        if (request.getNivelJerarquico() != null) modulo.setNivelJerarquico(request.getNivelJerarquico());
        if (request.getBreadcrumb() != null) modulo.setBreadcrumb(request.getBreadcrumb());
        if (request.getPaginaInicio() != null) modulo.setPaginaInicio(request.getPaginaInicio());
        if (request.getUrlAyuda() != null) modulo.setUrlAyuda(request.getUrlAyuda());
        if (request.getEtiqueta() != null) modulo.setEtiqueta(request.getEtiqueta());
        if (request.getComponenteAngular() != null) modulo.setComponenteAngular(request.getComponenteAngular());
        if (request.getVisibleEnMenu() != null) modulo.setVisibleEnMenu(request.getVisibleEnMenu());
        if (request.getVisibleEnDashboard() != null) modulo.setVisibleEnDashboard(request.getVisibleEnDashboard());
        if (request.getPermiteAccesoDirecto() != null) modulo.setPermiteAccesoDirecto(request.getPermiteAccesoDirecto());
        if (request.getRequiereAutenticacion() != null) modulo.setRequiereAutenticacion(request.getRequiereAutenticacion());
    }

    private void validarCamposTecnicos(ModuloRequest request, boolean creacion) {
        if ("solicitudes".equals(request.getCodigo())
                || esRutaRetirada(request.getRuta(), "/solicitudes")
                || esRutaRetirada(request.getRutaApi(), "/api/solicitudes")) {
            throw new IllegalArgumentException("El módulo Solicitudes fue retirado y ya no tiene pantalla ni API");
        }
        if (request.getNombre() != null && request.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del módulo no puede estar vacío");
        }
        if (creacion && (request.getRuta() == null || request.getRuta().isBlank())) {
            throw new IllegalArgumentException("La ruta del módulo es obligatoria");
        }
        if (request.getRuta() != null && !request.getRuta().matches("^/[a-z0-9][a-z0-9/_-]*$")) {
            throw new IllegalArgumentException("La ruta debe ser interna y usar únicamente minúsculas, números, /, _ o -");
        }
        if (request.getCodigo() != null
                && !request.getCodigo().matches("^[a-z0-9][a-z0-9_-]*$")) {
            throw new IllegalArgumentException("El código técnico solo admite minúsculas, números, _ o -");
        }
        if (request.getColor() != null && !request.getColor().matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException("El color debe tener formato hexadecimal #RRGGBB");
        }
    }

    private boolean esRutaRetirada(String ruta, String base) {
        return ruta != null && (ruta.equals(base) || ruta.startsWith(base + "/"));
    }

    private void validarCampoInmutable(String campo, String recibido, String actual) {
        if (recibido != null && !java.util.Objects.equals(recibido, actual)) {
            throw new IllegalArgumentException("El " + campo + " identifica una funcionalidad instalada y no puede modificarse");
        }
    }

    private String resumen(Modulo modulo) {
        return "{nombre=" + modulo.getNombre()
                + ", codigo=" + modulo.getCodigo()
                + ", ruta=" + modulo.getRuta()
                + ", activo=" + modulo.getActivo()
                + ", orden=" + modulo.getOrden()
                + ", visibleEnMenu=" + modulo.getVisibleEnMenu()
                + ", visibleEnDashboard=" + modulo.getVisibleEnDashboard() + "}";
    }

    private ModuloResponse toResponse(Modulo modulo) {
        ModuloResponse resp = new ModuloResponse();
        resp.setId(modulo.getId());
        resp.setNombre(modulo.getNombre());
        resp.setRuta(modulo.getRuta());
        resp.setIcono(modulo.getIcono());
        resp.setActivo(modulo.getActivo());
        resp.setOrden(modulo.getOrden());
        resp.setCodigo(modulo.getCodigo());
        resp.setDescripcion(modulo.getDescripcion());
        resp.setCategoria(modulo.getCategoria());
        resp.setColor(modulo.getColor());
        resp.setPadreId(modulo.getPadre() != null ? modulo.getPadre().getId() : null);
        resp.setPadreNombre(modulo.getPadre() != null ? modulo.getPadre().getNombre() : null);
        resp.setRutaApi(modulo.getRutaApi());
        resp.setTipoModulo(modulo.getTipoModulo());
        resp.setNivelJerarquico(modulo.getNivelJerarquico());
        resp.setBreadcrumb(modulo.getBreadcrumb());
        resp.setPaginaInicio(modulo.getPaginaInicio());
        resp.setUrlAyuda(modulo.getUrlAyuda());
        resp.setEtiqueta(modulo.getEtiqueta());
        resp.setComponenteAngular(modulo.getComponenteAngular());
        resp.setVisibleEnMenu(modulo.getVisibleEnMenu());
        resp.setVisibleEnDashboard(modulo.getVisibleEnDashboard());
        resp.setPermiteAccesoDirecto(modulo.getPermiteAccesoDirecto());
        resp.setRequiereAutenticacion(modulo.getRequiereAutenticacion());
        resp.setCreatedAt(modulo.getCreatedAt());
        resp.setUpdatedAt(modulo.getUpdatedAt());
        return resp;
    }

    private void logAudit(String accion, String entidad, Long entidadId, String detalle) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = auth != null ? auth.getName() : "sistema";
        AuditLog log = new AuditLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setDetalle(detalle);
        log.setDireccionIp(obtenerDireccionIp());
        log.setFecha(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    private String obtenerDireccionIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
