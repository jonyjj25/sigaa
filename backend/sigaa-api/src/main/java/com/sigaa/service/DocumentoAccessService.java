package com.sigaa.service;

import com.sigaa.model.Carpeta;
import com.sigaa.model.Documento;
import com.sigaa.model.DocumentoClasificacion;
import com.sigaa.model.DocumentoUsuarioAcceso;
import com.sigaa.model.Usuario;
import com.sigaa.repository.CarpetaRepository;
import com.sigaa.repository.CarpetaUsuarioRepository;
import com.sigaa.repository.DocumentoSolicitudAprobacionRepository;
import com.sigaa.repository.DocumentoUsuarioAccesoRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import com.sigaa.model.UsuarioUnidad;

@Service
@Transactional(readOnly = true)
public class DocumentoAccessService {
    private final CarpetaRepository carpetas;
    private final CarpetaUsuarioRepository carpetaUsuarios;
    private final DocumentoUsuarioAccesoRepository documentoUsuarios;
    private final DocumentoSolicitudAprobacionRepository aprobaciones;
    private final UsuarioUnidadRepository usuarioUnidades;

    public DocumentoAccessService(CarpetaRepository carpetas,
                                  CarpetaUsuarioRepository carpetaUsuarios,
                                  DocumentoUsuarioAccesoRepository documentoUsuarios,
                                  DocumentoSolicitudAprobacionRepository aprobaciones,
                                  UsuarioUnidadRepository usuarioUnidades) {
        this.carpetas = carpetas;
        this.carpetaUsuarios = carpetaUsuarios;
        this.documentoUsuarios = documentoUsuarios;
        this.aprobaciones = aprobaciones;
        this.usuarioUnidades = usuarioUnidades;
    }

    public boolean canView(Documento documento, Usuario usuario) {
        return canView(documento, usuario, null);
    }

    private boolean canView(Documento documento, Usuario usuario, ContextoListado contexto) {
        if (documento == null || documento.isEliminado() || usuario == null || !isUsable(usuario)) return false;
        if (isAdmin(usuario)) return true;
        if (isOwner(documento, usuario) || hasCustodialAccess(documento, usuario)) return true;

        DocumentoClasificacion clasificacion = classification(documento);
        return switch (clasificacion) {
            case PUBLICO -> true;
            case USO_INTERNO -> belongsToArea(documento, usuario, contexto);
            case RESTRINGIDO -> belongsToArea(documento, usuario, contexto);
            case CONFIDENCIAL, DATOS_SENSIBLES -> hasActiveShare(documento, usuario, contexto).isPresent()
                || isPendingApprover(documento, usuario, contexto);
        };
    }

    public boolean canModify(Documento documento, Usuario usuario) {
        return canModify(documento, usuario, null);
    }

    private boolean canModify(Documento documento, Usuario usuario, ContextoListado contexto) {
        if (!canView(documento, usuario, contexto)) return false;
        if (isAdmin(usuario)) return true;
        if (isOwner(documento, usuario) || hasCustodialAccess(documento, usuario)) return true;
        if (classification(documento).politica() != DocumentoClasificacion.CONFIDENCIAL
                && isFolderOwner(documento, usuario, contexto)) return true;
        Optional<DocumentoUsuarioAcceso> share = hasActiveShare(documento, usuario, contexto);
        if (share.isPresent() && "EDICION".equalsIgnoreCase(share.get().getPermiso())) return true;
        return canUnitEdit(documento, usuario, contexto);
    }

    public boolean canManageAccess(Documento documento, Usuario usuario) {
        return canManageAccess(documento, usuario, null);
    }

    /**
     * El borrado es más estricto que la edición o la gestión de accesos: ser
     * dueño de una carpeta, editor compartido o jefe de unidad no transfiere la
     * propiedad del documento.
     */
    public boolean canDelete(Documento documento, Usuario usuario) {
        return documento != null && usuario != null && isUsable(usuario)
                && (isOwner(documento, usuario) || hasCustodialAccess(documento, usuario));
    }

    private boolean canManageAccess(Documento documento, Usuario usuario, ContextoListado contexto) {
        return canView(documento, usuario, contexto)
                && (isAdmin(usuario) || isOwner(documento, usuario) || hasCustodialAccess(documento, usuario)
                    || (classification(documento).politica() != DocumentoClasificacion.CONFIDENCIAL
                        && isFolderOwner(documento, usuario, contexto)));
    }

    public boolean canAccessFolder(Carpeta carpeta, Usuario usuario) {
        return canAccessFolder(carpeta, usuario, null);
    }

    private boolean canAccessFolder(Carpeta carpeta, Usuario usuario, Set<Long> compartidas) {
        if (carpeta == null || usuario == null || !isUsable(usuario)) return false;
        if (isAdmin(usuario)) return true;
        if (carpeta.getPropietario() == null) return isAdmin(usuario);
        if (carpeta.getPropietario().getId().equals(usuario.getId())) return true;
        return "COMPARTIDA".equalsIgnoreCase(carpeta.getTipo())
                && (compartidas == null ? carpetaUsuarios.existsByCarpetaIdAndUsuarioId(carpeta.getId(), usuario.getId()) : compartidas.contains(carpeta.getId()));
    }

    public boolean canDeleteFolder(Carpeta carpeta, Usuario usuario) {
        return carpeta != null && usuario != null && isUsable(usuario)
                && ((carpeta.getPropietario() != null
                    && carpeta.getPropietario().getId().equals(usuario.getId()))
                    || ("PERSONAL".equalsIgnoreCase(carpeta.getTipo()) && isAdmin(usuario)));
    }

    public boolean isOwner(Documento documento, Usuario usuario) {
        if (documento == null || usuario == null) return false;
        if (documento.getPropietarioUsuarioId() != null) {
            return documento.getPropietarioUsuarioId().equals(usuario.getId());
        }
        String legacyOwner = normalize(documento.getPropietario());
        return !legacyOwner.isBlank() && (legacyOwner.equals(normalize(usuario.getCorreo()))
                || legacyOwner.equals(normalize(fullName(usuario))));
    }

    private Optional<Carpeta> folder(Documento documento, ContextoListado contexto) {
        return contexto == null ? carpetas.findById(documento.getCarpetaId()) : Optional.ofNullable(contexto.carpetas().get(documento.getCarpetaId()));
    }

    private boolean isFolderOwner(Documento documento, Usuario usuario, ContextoListado contexto) {
        if (documento.getCarpetaId() == null) return false;
        return folder(documento, contexto)
                .filter(folder -> folder.getPropietario() != null)
                .map(folder -> folder.getPropietario().getId().equals(usuario.getId()))
                .orElse(false);
    }

    private Optional<DocumentoUsuarioAcceso> hasActiveShare(Documento documento, Usuario usuario, ContextoListado contexto) {
        if (documento.getId() == null || usuario.getId() == null) return Optional.empty();
        return (contexto == null ? documentoUsuarios.findByDocumentoIdAndUsuarioId(documento.getId(), usuario.getId()) : Optional.ofNullable(contexto.accesos().get(documento.getId())))
                .filter(access -> access.getExpiraEn() == null || access.getExpiraEn().isAfter(LocalDateTime.now()));
    }

    private boolean isPendingApprover(Documento documento, Usuario usuario, ContextoListado contexto) {
        if (documento.getId() == null || usuario.getId() == null) return false;
        return contexto == null ? aprobaciones.existsByDocumentoIdAndAprobadorIdAndEstado(documento.getId(), usuario.getId(), "PENDIENTE") : contexto.pendientes().contains(documento.getId());
    }

    private boolean belongsToArea(Documento documento, Usuario usuario, ContextoListado contexto) {
        String area = normalize(documento.getArea());
        if (area.isBlank() || "personal".equals(area)) return false;
        return (contexto == null ? usuarioUnidades.findByUsuarioId(usuario.getId()) : contexto.membresias()).stream()
                .anyMatch(membership -> membership.getUnidad() != null
                        && area.equals(normalize(membership.getUnidad().getNombre())));
    }

    private boolean canUnitEdit(Documento documento, Usuario usuario, ContextoListado contexto) {
        DocumentoClasificacion clasificacion = classification(documento);
        if (clasificacion == DocumentoClasificacion.CONFIDENCIAL
                || clasificacion == DocumentoClasificacion.DATOS_SENSIBLES) return false;
        if (!"editar".equalsIgnoreCase(documento.getPermiso())
                && !"administrar".equalsIgnoreCase(documento.getPermiso())) return false;
        String area = normalize(documento.getArea());
        return (contexto == null ? usuarioUnidades.findByUsuarioId(usuario.getId()) : contexto.membresias()).stream().anyMatch(membership ->
                membership.getUnidad() != null && area.equals(normalize(membership.getUnidad().getNombre()))
                        && membership.getRol() != null
                        && "jefe de unidad".equals(normalize(membership.getRol().getNombre())));
    }

    private boolean hasCustodialAccess(Documento documento, Usuario usuario) {
        return documento.getPropietarioUsuarioId() == null && isAdmin(usuario);
    }

    public record Acceso(boolean ver, boolean editar, boolean administrar) {}
    private record ContextoListado(Map<Long, Carpeta> carpetas,
        Map<Long, DocumentoUsuarioAcceso> accesos, Set<Long> pendientes, List<UsuarioUnidad> membresias) {}

    /** Carga las dependencias una vez, reutilizando exactamente las reglas de acceso individuales. */
    public Map<Long, Acceso> evaluarListado(List<Documento> documentos, Usuario usuario) {
        Map<Long, Acceso> resultado = new HashMap<>();
        if (documentos.isEmpty()) return resultado;
        ContextoListado contexto = null;
        if (usuario != null && isUsable(usuario) && documentos.stream().anyMatch(d -> !isOwner(d, usuario) && !hasCustodialAccess(d, usuario))) {
            Set<Long> ids = documentos.stream().map(Documento::getCarpetaId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            Map<Long, Carpeta> folders = new HashMap<>();
            if (!ids.isEmpty()) carpetas.findAllById(ids).forEach(c -> folders.put(c.getId(), c));
            Map<Long, DocumentoUsuarioAcceso> shares = new HashMap<>();
            documentoUsuarios.findByUsuarioId(usuario.getId()).forEach(a -> shares.put(a.getDocumento().getId(), a));
            contexto = new ContextoListado(folders, shares,
                new java.util.HashSet<>(aprobaciones.findDocumentoIdsPendientesPorAprobador(usuario.getId())), usuarioUnidades.findByUsuarioId(usuario.getId()));
        }
        for (Documento d : documentos) resultado.put(d.getId(), new Acceso(canView(d, usuario, contexto), canModify(d, usuario, contexto), canManageAccess(d, usuario, contexto)));
        return resultado;
    }

    public List<Carpeta> filtrarCarpetas(List<Carpeta> lista, Usuario usuario) {
        if (usuario == null || !isUsable(usuario)) return List.of();
        Set<Long> compartidas = carpetasCompartidas(usuario);
        return lista.stream().filter(c -> canAccessFolder(c, usuario, compartidas)).toList();
    }

    private Set<Long> carpetasCompartidas(Usuario usuario) {
        return carpetaUsuarios.findByUsuarioId(usuario.getId()).stream().map(a -> a.getCarpeta().getId()).collect(java.util.stream.Collectors.toSet());
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> Boolean.TRUE.equals(role.getActivo()) && InstitutionalRoles.operational(role.getNombre()));
    }

    private boolean isUsable(Usuario usuario) {
        return Boolean.TRUE.equals(usuario.getActivo()) && !Boolean.TRUE.equals(usuario.getBloqueado());
    }

    private DocumentoClasificacion classification(Documento documento) {
        return documento.getClasificacion() == null ? DocumentoClasificacion.USO_INTERNO : documento.getClasificacion();
    }

    private String fullName(Usuario usuario) {
        return ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
                + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
