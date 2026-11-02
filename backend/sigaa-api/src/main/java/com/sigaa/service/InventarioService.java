package com.sigaa.service;

import com.sigaa.dto.*;
import com.sigaa.model.AuditLog;
import com.sigaa.model.InventarioActivo;
import com.sigaa.model.OrgUnit;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.InventarioActivoRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.OrgUnitRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class InventarioService {
    private final InventarioActivoRepository repository;
    private final AuditLogRepository auditRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrgUnitRepository unidadRepository;

    public InventarioService(InventarioActivoRepository repository, AuditLogRepository auditRepository,
                             UsuarioRepository usuarioRepository, OrgUnitRepository unidadRepository) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.usuarioRepository = usuarioRepository;
        this.unidadRepository = unidadRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, List<InventarioResponse.OpcionAsignacion>> asignables() {
        Usuario limitado = subdirectorActual();
        if (limitado != null) {
            return Map.of("personas", List.of(new InventarioResponse.OpcionAsignacion(limitado.getId(), nombrePersona(limitado))),
                "unidades", limitado.getMembresias().stream()
                    .filter(m -> m.getRol() != null && Boolean.TRUE.equals(m.getRol().getActivo()))
                    .map(m -> m.getUnidad()).filter(Objects::nonNull)
                    .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                    .map(u -> unidadRepository.findFirstByNombreIgnoreCaseAndActivoTrue(u.getNombre()))
                    .flatMap(Optional::stream)
                    .map(u -> new InventarioResponse.OpcionAsignacion(u.getId(), u.getNombre())).distinct().toList());
        }
        return Map.of("personas", usuarioRepository.findByActivoTrue().stream()
                .map(u -> new InventarioResponse.OpcionAsignacion(u.getId(), nombrePersona(u))).toList(),
                "unidades", unidadRepository.findByActivoTrueOrderByOrdenAscNombreAsc().stream()
                .map(u -> new InventarioResponse.OpcionAsignacion(u.getId(), u.getNombre())).toList());
    }

    @Transactional(readOnly = true)
    public List<InventarioResponse> listar() {
        Usuario limitado = subdirectorActual();
        return repository.findAllByOrderByCodigoAsc().stream()
            .filter(activo -> dentroDelAmbito(activo, limitado)).map(this::toResponse).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventarioResponse crear(InventarioCreateRequest request) {
        if (request.getCodigo() != null) throw new IllegalArgumentException("El código se asigna automáticamente al guardar");
        InventarioActivo activo = new InventarioActivo();
        aplicar(activo, request);
        exigirAmbito(activo);
        String prefijo = "INV-" + LocalDate.now().getYear() + "-";
        repository.bloquearGeneracionCodigo();
        long ultimo = repository.ultimoNumeroCodigo(prefijo);
        if (ultimo == Long.MAX_VALUE) throw new IllegalStateException("Se agotó la numeración de activos");
        activo.setCodigo(prefijo + String.format(Locale.ROOT, "%03d", ultimo + 1));
        InventarioActivo saved = repository.saveAndFlush(activo);
        auditar("CREAR", saved, "Se creó activo " + saved.getCodigo());
        return toResponse(saved);
    }

    @Transactional
    public InventarioResponse actualizar(Long id, InventarioUpdateRequest request) {
        InventarioActivo activo = obtener(id);
        if (request.getCodigo() != null && !request.getCodigo().equals(activo.getCodigo())) {
            throw new IllegalArgumentException("El código del activo no puede modificarse");
        }
        if (!Objects.equals(request.getLockVersion(), activo.getLockVersion())) {
            throw new ObjectOptimisticLockingFailureException(InventarioActivo.class, id);
        }
        InventarioResponse anterior = toResponse(activo);
        aplicar(activo, request);
        exigirAmbito(activo);
        String detalle = detalleEdicion(anterior, toResponse(activo));
        if (detalle.isEmpty()) return anterior;
        activo.setUpdatedAt(LocalDateTime.now());
        InventarioActivo saved = repository.saveAndFlush(activo);
        auditar("EDITAR", saved, detalle);
        return toResponse(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        InventarioActivo activo = obtener(id);
        repository.delete(activo);
        repository.flush();
        auditar("ELIMINAR", activo, "Se eliminó activo " + activo.getCodigo());
    }

    private InventarioActivo obtener(Long id) {
        InventarioActivo activo = repository.findById(id).orElseThrow(() -> new NoSuchElementException("Activo no encontrado"));
        exigirAmbito(activo);
        return activo;
    }

    private Usuario subdirectorActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!InstitutionalRoles.scopedSubdirector(auth)) return null;
        return usuarioRepository.findByCorreoIgnoreCase(auth.getName())
            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Usuario no disponible"));
    }

    private boolean dentroDelAmbito(InventarioActivo activo, Usuario usuario) {
        if (usuario == null) return true;
        if (activo.getAsignadoUsuario() != null && usuario.getId().equals(activo.getAsignadoUsuario().getId())) return true;
        return activo.getAsignadoUnidad() != null && usuario.getMembresias().stream()
            .anyMatch(m -> m.getRol() != null && Boolean.TRUE.equals(m.getRol().getActivo())
                && m.getUnidad() != null
                && activo.getAsignadoUnidad().getNombre().equalsIgnoreCase(m.getUnidad().getNombre()));
    }

    private void exigirAmbito(InventarioActivo activo) {
        if (!dentroDelAmbito(activo, subdirectorActual())) {
            throw new org.springframework.security.access.AccessDeniedException("Sin acceso a este activo");
        }
    }

    private void aplicar(InventarioActivo activo, InventarioActivoRequest request) {
        activo.setNombre(texto(request.getNombre()));
        activo.setCategoria(texto(request.getCategoria()));
        activo.setUbicacion(texto(request.getUbicacion()));
        aplicarAsignacion(activo, request);
        activo.setEstado(request.getEstado());
        activo.setMarcaModelo(texto(request.getMarcaModelo()));
        activo.setSerie(texto(request.getSerie()));
        activo.setSerieInstitucional(texto(request.getSerieInstitucional()));
        activo.setFechaAdquisicion(request.getFechaAdquisicion());
        activo.setProveedor(texto(request.getProveedor()));
        activo.setDescripcion(texto(request.getDescripcion()));
    }

    private void aplicarAsignacion(InventarioActivo activo, InventarioActivoRequest request) {
        if (!request.isAsignacionValida()) throw new IllegalArgumentException("La asignación es inconsistente");
        if (!texto(request.getUnidad()).isEmpty() || !texto(request.getResponsable()).isEmpty()) {
            throw new IllegalArgumentException("Selecciona una persona o unidad de SIGAA; no se admite asignación por texto libre");
        }
        Usuario persona = null;
        OrgUnit unidad = null;
        if ("PERSONA".equals(request.getTipoAsignacion())) {
            persona = usuarioRepository.findById(request.getAsignadoUsuarioId())
                    .orElseThrow(() -> new IllegalArgumentException("La persona seleccionada no existe"));
            boolean mismaPersona = activo.getAsignadoUsuario() != null
                    && Objects.equals(activo.getAsignadoUsuario().getId(), persona.getId());
            if (!Boolean.TRUE.equals(persona.getActivo()) && !mismaPersona) {
                throw new IllegalArgumentException("La persona seleccionada está inactiva");
            }
        } else if ("UNIDAD".equals(request.getTipoAsignacion())) {
            unidad = unidadRepository.findById(request.getAsignadoUnidadId())
                    .orElseThrow(() -> new IllegalArgumentException("La unidad seleccionada no existe"));
            boolean mismaUnidad = activo.getAsignadoUnidad() != null
                    && Objects.equals(activo.getAsignadoUnidad().getId(), unidad.getId());
            if (!Boolean.TRUE.equals(unidad.getActivo()) && !mismaUnidad) {
                throw new IllegalArgumentException("La unidad seleccionada está inactiva");
            }
        }
        activo.setAsignadoUsuario(persona);
        activo.setAsignadoUnidad(unidad);
        activo.setResponsable(persona == null ? "" : nombrePersona(persona));
        activo.setUnidad(unidad == null ? "" : unidad.getNombre());
    }

    private String nombrePersona(Usuario usuario) {
        return java.util.stream.Stream.of(usuario.getNombre(), usuario.getSegundoNombre(), usuario.getApellido())
                .map(this::texto).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.joining(" "));
    }

    private String tipoAsignacion(InventarioActivo activo) {
        if (activo.getAsignadoUsuario() != null) return "PERSONA";
        if (activo.getAsignadoUnidad() != null) return "UNIDAD";
        if (!texto(activo.getResponsable()).isEmpty() || !texto(activo.getUnidad()).isEmpty()) return "PENDIENTE_REVISION";
        return "SIN_ASIGNAR";
    }

    private InventarioResponse toResponse(InventarioActivo activo) {
        return new InventarioResponse(String.valueOf(activo.getId()), activo.getCodigo(), activo.getNombre(),
                activo.getCategoria(), activo.getUbicacion(), texto(activo.getUnidad()), activo.getEstado(),
                activo.getFechaAdquisicion() == null ? "" : activo.getFechaAdquisicion().toString(),
                activo.getMarcaModelo(), activo.getSerie(), activo.getSerieInstitucional(),
                activo.getResponsable(), activo.getProveedor(), activo.getDescripcion(), activo.getLockVersion(),
                tipoAsignacion(activo), activo.getAsignadoUsuario() == null ? null : activo.getAsignadoUsuario().getId(),
                activo.getAsignadoUnidad() == null ? null : activo.getAsignadoUnidad().getId());
    }

    private String detalleEdicion(InventarioResponse antes, InventarioResponse despues) {
        List<String> cambios = new ArrayList<>();
        cambio(cambios, "nombre", antes.nombre(), despues.nombre());
        cambio(cambios, "categoria", antes.categoria(), despues.categoria());
        cambio(cambios, "ubicacion", antes.ubicacion(), despues.ubicacion());
        cambio(cambios, "unidad", antes.unidad(), despues.unidad());
        cambio(cambios, "responsable", antes.responsable(), despues.responsable());
        cambio(cambios, "tipoAsignacion", antes.tipoAsignacion(), despues.tipoAsignacion());
        cambio(cambios, "asignadoUsuarioId", antes.asignadoUsuarioId(), despues.asignadoUsuarioId());
        cambio(cambios, "asignadoUnidadId", antes.asignadoUnidadId(), despues.asignadoUnidadId());
        cambio(cambios, "estado", antes.estado(), despues.estado());
        cambio(cambios, "marcaModelo", antes.marcaModelo(), despues.marcaModelo());
        cambio(cambios, "serie", antes.serie(), despues.serie());
        cambio(cambios, "serieInstitucional", antes.serieInstitucional(), despues.serieInstitucional());
        cambio(cambios, "fechaAdquisicion", antes.fechaAdquisicion(), despues.fechaAdquisicion());
        cambio(cambios, "proveedor", antes.proveedor(), despues.proveedor());
        cambio(cambios, "descripcion", antes.descripcion(), despues.descripcion());
        return cambios.isEmpty() ? "" : "Se editó activo " + antes.codigo() + ": " + String.join("; ", cambios);
    }

    private void cambio(List<String> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(campo + ": '" + Objects.toString(antes, "") + "' -> '" + Objects.toString(despues, "") + "'");
        }
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private void auditar(String accion, InventarioActivo activo, String detalle) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        AuditLog log = new AuditLog();
        log.setUsuario(authentication == null ? "sistema" : authentication.getName());
        log.setAccion(accion);
        log.setEntidad("InventarioActivo");
        log.setEntidadId(activo.getId());
        log.setDetalle(detalle);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            log.setDireccionIp(attributes.getRequest().getRemoteAddr());
        }
        auditRepository.saveAndFlush(log);
    }
}
