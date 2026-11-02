package com.sigaa.service;

import com.sigaa.dto.*;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GrupoService {
    private final GrupoRepository grupos;
    private final GrupoUsuarioRepository miembros;
    private final UnidadRepository unidades;
    private final UsuarioRepository usuarios;
    private final PermissionRepository permisos;
    private final AuditLogRepository auditoria;
    private final GrupoMensajeRepository mensajes;
    private final InternalNotificationService notificaciones;
    private final UsuarioUnidadRepository usuarioUnidades;
    private final TareaRepository tareas;
    private final DocumentoRepository documentos;
    private final DocumentoAccessService documentoAccess;

    public GrupoService(GrupoRepository grupos, GrupoUsuarioRepository miembros, UnidadRepository unidades,
                        UsuarioRepository usuarios, PermissionRepository permisos, AuditLogRepository auditoria, GrupoMensajeRepository mensajes,
                        InternalNotificationService notificaciones, UsuarioUnidadRepository usuarioUnidades, TareaRepository tareas, DocumentoRepository documentos,
                        DocumentoAccessService documentoAccess) {
        this.grupos = grupos; this.miembros = miembros; this.unidades = unidades;
        this.usuarios = usuarios; this.permisos = permisos; this.auditoria = auditoria; this.mensajes = mensajes; this.notificaciones = notificaciones; this.usuarioUnidades = usuarioUnidades; this.tareas = tareas; this.documentos = documentos;
        this.documentoAccess = documentoAccess;
    }

    /** The list is always scoped server-side. A client filter can never broaden it. */
    public List<GrupoResponse> listar(String q, String estado, Long unidadId) {
        Usuario usuario = actual();
        Set<Long> unidadesJefatura = unidadesJefatura(usuario);
        List<Grupo> base = q == null || q.isBlank() ? grupos.findAll() : grupos.buscar(q);
        return base.stream()
            .filter(grupo -> puedeVer(usuario, unidadesJefatura, grupo))
            .filter(grupo -> unidadId == null || grupo.getUnidad().getId().equals(unidadId))
            .filter(grupo -> estado == null || "todos".equals(estado) || ("activo".equals(estado) == grupo.getActivo()))
            .map(this::dto)
            .toList();
    }

    public GrupoResponse obtener(Long id) {
        Grupo grupo = buscarGrupo(id);
        accesoLectura(grupo);
        return dto(grupo);
    }

    @Transactional
    public GrupoResponse crear(GrupoRequest request) {
        Unidad unidad = unidadActiva(request.unidadId());
        validarJefaturaUnidad(unidad);
        if (grupos.existsByUnidadIdAndNombreIgnoreCase(unidad.getId(), request.nombre().trim())) {
            throw new IllegalArgumentException("El grupo ya existe en esta unidad");
        }
        validarPermisosDeGrupo(request.permisoIds());
        Grupo grupo = new Grupo();
        aplicar(grupo, request, unidad);
        grupo.setCreadoPor(actual());
        Grupo guardado = grupos.save(grupo);
        asignarMiembros(guardado, request.miembroIds(), request.responsableId());
        asignarPermisos(guardado, request.permisoIds());
        auditar("CREAR", guardado, "Grupo creado: " + guardado.getNombre());
        return dto(guardado);
    }

    @Transactional
    public GrupoResponse actualizar(Long id, GrupoRequest request) {
        Grupo grupo = buscarGrupo(id);
        accesoAdministracion(grupo);
        Unidad unidad = unidadActiva(request.unidadId());
        if (!esSubdirector(actual()) || !grupo.getUnidad().getId().equals(unidad.getId())) validarJefaturaUnidad(unidad);
        if (request.permisoIds() != null && !new HashSet<>(request.permisoIds()).equals(
                grupo.getPermisos().stream().map(p -> p.getId()).collect(Collectors.toSet()))) {
            validarPermisosDeGrupo(request.permisoIds());
        }
        aplicar(grupo, request, unidad);
        asignarMiembros(grupo, request.miembroIds(), request.responsableId());
        asignarPermisos(grupo, request.permisoIds());
        auditar("ACTUALIZAR", grupo, "Grupo actualizado: " + grupo.getNombre());
        return dto(grupo);
    }

    @Transactional
    public GrupoResponse cambiarEstado(Long id) {
        Grupo grupo = buscarGrupo(id);
        accesoAdministracion(grupo);
        grupo.setActivo(!grupo.getActivo());
        auditar("CAMBIAR_ESTADO", grupo, "Estado del grupo: " + grupo.getActivo());
        return dto(grupo);
    }

    @Transactional
    public void eliminar(Long id) {
        Grupo grupo = buscarGrupo(id);
        if (!esAdmin(actual()) && !esSubdirector(actual())) throw new AccessDeniedException("Solo un administrador puede eliminar grupos");
        accesoAdministracion(grupo);
        if (!grupo.getMiembros().isEmpty()) {
            throw new IllegalArgumentException("El grupo tiene miembros; desactívelo o retire sus miembros antes de eliminarlo");
        }
        grupos.delete(grupo);
        auditar("ELIMINAR", grupo, "Grupo eliminado: " + grupo.getNombre());
    }

    public List<GrupoMiembroResponse> listarMiembros(Long id) {
        obtener(id);
        return miembros.findByGrupoIdOrderByFechaAsignacionAsc(id).stream()
            .filter(m -> !ParticipantePolicy.esAdministrador(m.getUsuario()))
            .map(m -> new GrupoMiembroResponse(m.getId(), m.getUsuario().getId(),
                m.getUsuario().getNombre() + " " + m.getUsuario().getApellido(), m.getUsuario().getCorreo(),
                m.getResponsable(), m.getActivo(), m.getFechaAsignacion()))
            .toList();
    }

    @Transactional
    public List<GrupoMiembroResponse> actualizarMiembros(Long id, List<Long> miembroIds) {
        Grupo grupo = buscarGrupo(id);
        accesoAdministracion(grupo);

        // El responsable de la unidad siempre debe conservarse como miembro.
        Long responsableId = grupo.getMiembros().stream()
            .filter(GrupoUsuario::getResponsable)
            .map(miembro -> miembro.getUsuario().getId())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("El grupo no tiene un responsable asignado"));
        List<Long> ids = new ArrayList<>(miembroIds == null ? List.of() : miembroIds);
        if (!ids.contains(responsableId)) ids.add(responsableId);

        asignarMiembros(grupo, ids, responsableId);
        auditar("ACTUALIZAR_MIEMBROS", grupo, "Miembros actualizados: " + grupo.getNombre());
        return listarMiembros(id);
    }

    public Map<String, Long> estadisticas() {
        List<GrupoResponse> visibles = listar(null, "todos", null);
        return Map.of("total", (long) visibles.size(),
            "activos", visibles.stream().filter(GrupoResponse::activo).count(),
            "inactivos", visibles.stream().filter(g -> !g.activo()).count(),
            "miembros", visibles.stream().mapToLong(GrupoResponse::miembros).sum());
    }

    public List<Unidad> unidadesVisibles() {
        Usuario usuario = actual();
        if (esAdmin(usuario)) return unidades.findByActivoTrue();
        Set<Long> ids = unidadesJefatura(usuario);
        return unidades.findByActivoTrue().stream().filter(u -> ids.contains(u.getId())).toList();
    }

    public List<AuditLog> actividades(Long id) {
        obtener(id);
        return auditoria.findByEntidadAndEntidadIdOrderByFechaDesc("Grupo", id);
    }

    public Map<String, Object> resumen(Long id) {
        Grupo grupo = buscarGrupo(id); accesoLectura(grupo);
        long activos = grupo.getMiembros().stream().filter(GrupoUsuario::getActivo).count();
        List<Tarea> tareasUnidad = tareasDeUnidad(grupo);
        return Map.of("miembros", grupo.getMiembros().size(), "miembrosActivos", activos,
            "archivos", (long) archivosDeUnidad(grupo).size(), "tareasActivas", tareasUnidad.stream().filter(t -> !"completada".equalsIgnoreCase(t.getEstado())).count(), "tareasPendientes", tareasUnidad.stream().filter(t -> "por_hacer".equalsIgnoreCase(t.getEstado())).count(), "reuniones", 0L, "proximaReunion", "");
    }

    public List<Map<String,Object>> tareas(Long id) { Grupo grupo=buscarGrupo(id); accesoLectura(grupo); return tareasDeUnidad(grupo).stream().map(t -> Map.<String,Object>of("id",t.getId(),"titulo",t.getTitulo(),"asignado",t.getAsignado()==null?"":t.getAsignado(),"fechaLimite",t.getFechaLimite()==null?"":t.getFechaLimite().toString(),"prioridad",t.getPrioridad(),"estado",t.getEstado(),"progreso",t.getProgreso())).toList(); }
    public List<Map<String,Object>> archivos(Long id) { Grupo grupo=buscarGrupo(id); accesoLectura(grupo); return archivosDeUnidad(grupo).stream().map(d -> Map.<String,Object>of("id",d.getId(),"nombre",d.getNombre(),"tipo",d.getCategoria()==null?"Archivo":d.getCategoria(),"tamano",d.getTamano()==null?"":d.getTamano(),"propietario",d.getPropietario()==null?"":d.getPropietario(),"fecha",d.getFecha()==null?"":d.getFecha().toString())).toList(); }
    private List<Tarea> tareasDeUnidad(Grupo grupo){ String unidad=grupo.getUnidad().getNombre().trim().toLowerCase(Locale.ROOT); return tareas.findAllByOrderByFechaCreacionDesc().stream().filter(t -> Arrays.stream((t.getUnidades()==null?"":t.getUnidades()).split(",")).anyMatch(v -> unidad.equals(v.trim().toLowerCase(Locale.ROOT)))).toList(); }
    private List<Documento> archivosDeUnidad(Grupo grupo) {
        String unidad = grupo.getUnidad().getNombre().trim();
        Usuario usuario = actual();
        List<Documento> candidatos = documentos.findByAreaIgnoreCaseOrderByFechaDesc(unidad);
        Map<Long, DocumentoAccessService.Acceso> accesos = documentoAccess.evaluarListado(candidatos, usuario);
        return candidatos.stream().filter(d -> accesos.get(d.getId()).ver()).toList();
    }

    public org.springframework.data.domain.Page<GrupoMensajeResponse> mensajes(Long id, int pagina, int tamano) {
        Grupo grupo = buscarGrupo(id); accesoLectura(grupo);
        return mensajes.findByGrupoIdAndEliminadoFalseOrderByCreadoEnDesc(id, org.springframework.data.domain.PageRequest.of(Math.max(0,pagina), Math.min(Math.max(1,tamano),100)))
            .map(this::mensajeDto);
    }

    @Transactional public GrupoMensajeResponse enviarMensaje(Long id, GrupoMensajeRequest request) {
        Grupo grupo = buscarGrupo(id); accesoLectura(grupo);
        GrupoMensaje mensaje = new GrupoMensaje(); mensaje.setGrupo(grupo); mensaje.setAutor(actual());
        mensaje.setContenido(sanitizarTexto(request.contenido()));
        GrupoMensajeResponse respuesta = mensajeDto(mensajes.save(mensaje));
        Set<Long> mencionados = request.mencionadosIds() == null ? Set.of() : new HashSet<>(request.mencionadosIds());
        Set<Long> miembrosActivos = grupo.getMiembros().stream().filter(GrupoUsuario::getActivo).map(m -> m.getUsuario().getId()).collect(Collectors.toSet());
        // También detecta menciones escritas manualmente, aunque no se haya
        // seleccionado una sugerencia del autocompletado del navegador.
        String contenidoNormalizado = mensaje.getContenido().toLowerCase(Locale.ROOT);
        grupo.getMiembros().stream()
            .filter(GrupoUsuario::getActivo)
            .map(GrupoUsuario::getUsuario)
            .filter(usuario -> {
                String nombreCompleto = (usuario.getNombre() + " " + usuario.getApellido()).trim().toLowerCase(Locale.ROOT);
                return !nombreCompleto.isBlank() && contenidoNormalizado.contains("@" + nombreCompleto);
            })
            .map(Usuario::getId)
            .forEach(mencionados::add);
        mencionados.retainAll(miembrosActivos);
        mencionados.remove(mensaje.getAutor().getId());
        grupo.getMiembros().stream()
            .filter(GrupoUsuario::getActivo)
            .map(GrupoUsuario::getUsuario)
            .filter(miembro -> !miembro.getId().equals(mensaje.getAutor().getId()))
            .forEach(miembro -> notificaciones.send(mencionados.contains(miembro.getId()) ? "mencion_grupo" : "mensaje_grupo", miembro,
                mencionados.contains(miembro.getId()) ? "Te mencionaron en " + grupo.getNombre() : "Nuevo mensaje en " + grupo.getNombre(),
                mencionados.contains(miembro.getId()) ? mensaje.getAutor().getNombre() + " te mencionó: " + mensaje.getContenido() : "Tienes un mensaje nuevo en el chat del grupo."));
        return respuesta;
    }

    @Transactional public void eliminarMensaje(Long grupoId, Long mensajeId) {
        Grupo grupo = buscarGrupo(grupoId); accesoLectura(grupo); GrupoMensaje mensaje = mensajes.findById(mensajeId).filter(m -> m.getGrupo().getId().equals(grupoId)).orElseThrow(() -> new NoSuchElementException("Mensaje no encontrado"));
        if (!mensaje.getAutor().getId().equals(actual().getId()) && !esAdmin(actual()) && !unidadesJefatura(actual()).contains(grupo.getUnidad().getId())) throw new AccessDeniedException("No puede eliminar este mensaje");
        mensaje.setEliminado(true);
    }
    private GrupoMensajeResponse mensajeDto(GrupoMensaje m){ Usuario a=m.getAutor(); return new GrupoMensajeResponse(m.getId(),a.getId(),(a.getNombre()+" "+a.getApellido()).trim(),m.getContenido(),m.getCreadoEn()); }
    private String sanitizarTexto(String texto){ return texto.trim().replaceAll("[<>]", ""); }

    private Grupo buscarGrupo(Long id) {
        return grupos.findById(id).orElseThrow(() -> new NoSuchElementException("Grupo no encontrado"));
    }

    private Unidad unidadActiva(Long id) {
        return unidades.findById(id).filter(Unidad::getActivo).orElseThrow(() -> new IllegalArgumentException("Unidad inválida"));
    }

    private void aplicar(Grupo grupo, GrupoRequest request, Unidad unidad) {
        grupo.setNombre(request.nombre().trim());
        grupo.setDescripcion(request.descripcion() == null ? "" : request.descripcion().trim());
        grupo.setUnidad(unidad); grupo.setTipo(request.tipo().trim()); grupo.setIcono(request.icono());
        grupo.setColor(request.color()); if (request.activo() != null) grupo.setActivo(request.activo());
    }

    private void asignarMiembros(Grupo grupo, List<Long> ids, Long responsable) {
        if (ids == null) return;
        Set<Long> unicos = new HashSet<>(ids);
        if (unicos.size() != ids.size()) throw new IllegalArgumentException("No puede repetir miembros");
        // The join table enforces a unique (grupo_id, usuario_id) pair. Clear and
        // flush the orphan-removal deletes before adding replacements so an edit
        // that keeps an existing member never conflicts with its previous row.
        grupo.getMiembros().clear();
        miembros.flush();
        for (Long usuarioId : unicos) {
            Usuario usuario = usuarios.findById(usuarioId).filter(Usuario::getActivo)
                .orElseThrow(() -> new IllegalArgumentException("Usuario inválido"));
            ParticipantePolicy.exigirPuedeParticipar(usuario);
            GrupoUsuario miembro = new GrupoUsuario();
            miembro.setGrupo(grupo); miembro.setUsuario(usuario); miembro.setResponsable(usuarioId.equals(responsable));
            miembros.save(miembro);
            grupo.getMiembros().add(miembro);
        }
    }

    private void asignarPermisos(Grupo grupo, List<Long> ids) {
        if (ids != null) grupo.setPermisos(new HashSet<>(permisos.findAllById(ids)));
    }

    private GrupoResponse dto(Grupo grupo) {
        GrupoUsuario responsable = grupo.getMiembros().stream().filter(GrupoUsuario::getResponsable).findFirst().orElse(null);
        Long responsableId = responsable == null ? null : responsable.getUsuario().getId();
        String responsableNombre = responsable == null ? "" : responsable.getUsuario().getNombre() + " " + responsable.getUsuario().getApellido();
        return new GrupoResponse(grupo.getId(), grupo.getNombre(), grupo.getDescripcion(), grupo.getUnidad().getId(),
            grupo.getUnidad().getNombre(), grupo.getTipo(), grupo.getIcono(), grupo.getColor(), grupo.getActivo(),
            grupo.getCreadoEn(), grupo.getCreadoPor() == null ? "" : grupo.getCreadoPor().getNombre(),
            (int) grupo.getMiembros().stream().filter(GrupoUsuario::getActivo)
                .filter(miembro -> !ParticipantePolicy.esAdministrador(miembro.getUsuario())).count(),
            responsableId, responsableNombre,
            grupo.getPermisos().stream().map(Permission::getId).toList());
    }

    private Usuario actual() {
        String correo = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarios.findByCorreoIgnoreCase(correo.trim())
            .orElseThrow(() -> new AccessDeniedException("La sesión no corresponde a un usuario válido"));
    }

    private boolean esAdmin(Usuario usuario) {
        return usuario.getRoles().stream().anyMatch(r -> Boolean.TRUE.equals(r.getActivo())
            && InstitutionalRoles.operational(r.getNombre()));
    }

    private boolean esSubdirector(Usuario usuario) {
        return !esAdmin(usuario) && usuario.getRoles().stream().anyMatch(r -> Boolean.TRUE.equals(r.getActivo())
            && "Subdirector".equals(InstitutionalRoles.canonical(r.getNombre())));
    }

    private Set<Long> unidadesJefatura(Usuario usuario) {
        return usuarioUnidades.findByUsuarioId(usuario.getId()).stream()
            .filter(m -> m.getRol() != null && (esSubdirector(usuario)
                ? Boolean.TRUE.equals(m.getRol().getActivo()) : "jefe de unidad".equalsIgnoreCase(m.getRol().getNombre())))
            .map(m -> m.getUnidad().getId()).collect(Collectors.toSet());
    }

    private boolean esMiembroActivo(Usuario usuario, Grupo grupo) {
        return grupo.getMiembros().stream().anyMatch(m -> m.getActivo() && m.getUsuario().getId().equals(usuario.getId()));
    }

    private boolean puedeVer(Usuario usuario, Set<Long> unidadesJefatura, Grupo grupo) {
        if (esAdmin(usuario)) return true;
        if (esSubdirector(usuario) && (esMiembroActivo(usuario, grupo)
                || (grupo.getCreadoPor() != null && usuario.getId().equals(grupo.getCreadoPor().getId())))) return true;
        return !unidadesJefatura.isEmpty() ? unidadesJefatura.contains(grupo.getUnidad().getId()) : esMiembroActivo(usuario, grupo);
    }

    private void accesoLectura(Grupo grupo) {
        Usuario usuario = actual();
        if (!puedeVer(usuario, unidadesJefatura(usuario), grupo)) throw new AccessDeniedException("Sin acceso al grupo");
    }

    private void accesoAdministracion(Grupo grupo) {
        Usuario usuario = actual();
        if (esSubdirector(usuario) && puedeVer(usuario, unidadesJefatura(usuario), grupo)) return;
        if (!esAdmin(usuario) && !unidadesJefatura(usuario).contains(grupo.getUnidad().getId())) {
            throw new AccessDeniedException("Solo el Jefe de la unidad puede administrar este grupo");
        }
    }

    private void validarJefaturaUnidad(Unidad unidad) {
        Usuario usuario = actual();
        if (!esAdmin(usuario) && !unidadesJefatura(usuario).contains(unidad.getId())) {
            throw new AccessDeniedException("Solo puede crear o trasladar grupos dentro de su unidad");
        }
    }

    private void validarPermisosDeGrupo(List<Long> permisoIds) {
        if (esSubdirector(actual()) && (permisoIds == null || permisoIds.isEmpty())) return;
        if (permisoIds != null && !esAdmin(actual())) {
            throw new AccessDeniedException("Solo un administrador puede asignar permisos al grupo");
        }
    }

    private void auditar(String accion, Grupo grupo, String detalle) {
        AuditLog log = new AuditLog(); log.setUsuario(SecurityContextHolder.getContext().getAuthentication().getName());
        log.setAccion(accion); log.setEntidad("Grupo"); log.setEntidadId(grupo.getId());
        log.setDetalle(detalle); log.setFecha(LocalDateTime.now()); auditoria.save(log);
    }
}
