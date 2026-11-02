package com.sigaa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigaa.util.LogDiagnostics;
import com.sigaa.dto.ReunionRequest;
import com.sigaa.dto.ReunionResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Reunion;
import com.sigaa.model.Usuario;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.ReunionRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReunionService {
    private static final Logger log = LoggerFactory.getLogger(ReunionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReunionRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioUnidadRepository usuarioUnidadRepository;
    private final InternalNotificationService notificationService;
    private final com.sigaa.repository.UnidadRepository unidadRepository;

    public ReunionService(ReunionRepository repository, AuditLogRepository auditLogRepository,
                          UsuarioRepository usuarioRepository, UsuarioUnidadRepository usuarioUnidadRepository,
                          InternalNotificationService notificationService, com.sigaa.repository.UnidadRepository unidadRepository) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioUnidadRepository = usuarioUnidadRepository;
        this.notificationService = notificationService;
        this.unidadRepository = unidadRepository;
    }

    @Transactional(readOnly = true)
    public List<ReunionResponse> listarTodas() {
        return respuestas(reunionesVisiblesActual());
    }

    @Transactional(readOnly = true)
    public List<ReunionResponse> listarPorEstado(String estado) {
        return respuestas(reunionesVisiblesActual().stream()
            .filter(reunion -> java.util.Objects.equals(estado, reunion.getEstado()))
            .toList());
    }

    @Transactional(readOnly = true)
    public List<ReunionResponse> buscar(String termino) {
        String query = termino == null ? "" : termino.trim().toLowerCase(Locale.ROOT);
        return respuestas(reunionesVisiblesActual().stream()
            .filter(reunion -> contiene(reunion.getTitulo(), query)
                || contiene(reunion.getOrganizador(), query)
                || contiene(reunion.getArea(), query)
                || contiene(reunion.getCodigo(), query))
            .toList());
    }

    @Transactional(readOnly = true)
    public ReunionResponse obtenerPorId(Long id) {
        Reunion entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Reunión no encontrada con id: " + id));
        exigirLectura(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public ReunionResponse obtenerPorCodigo(String codigo) {
        Reunion entity = repository.findByCodigo(codigo)
            .orElseThrow(() -> new NoSuchElementException("Reunión no encontrada con código: " + codigo));
        exigirLectura(entity);
        return toResponse(entity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReunionResponse crear(ReunionRequest request) {
        String codigo = generateCodigo();
        Reunion entity = new Reunion();
        entity.setCodigo(codigo);
        applyRequest(entity, request);
        Usuario creador = usuarioActual();
        if (creador == null) throw new AccessDeniedException("No se pudo identificar al creador de la reunión");
        entity.setOrganizadorUsuario(creador);
        entity.setOrganizador(nombreCompleto(creador));
        participantesIniciales(entity, creador);
        sincronizarUnidades(entity, request);
        if (entity.getEstado() == null) entity.setEstado("programada");
        if (!"programada".equals(entity.getEstado())) exigirPermisoEstado(entity.getEstado());

        Reunion saved = repository.save(entity);
        logAudit("CREAR", "Reunion", saved.getId(), "Reunión creada: [" + saved.getCodigo() + "] " + saved.getTitulo());
        if ("en_curso".equals(saved.getEstado())) notificarCambioEstado(saved);
        else programarNotificacionParticipantes(saved, "reunion_programada", "Nueva reuni\u00f3n: " + saved.getTitulo(),
                "Fuiste incluido en la reuni\u00f3n " + formatoReunion(saved) + ".");
        return toResponse(saved);
    }

    @Transactional
    public ReunionResponse actualizar(Long id, ReunionRequest request) {
        Reunion entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Reunión no encontrada con id: " + id));
        exigirGestion(entity);

        String estadoAnterior = entity.getEstado();
        applyRequest(entity, request);
        sincronizarUnidades(entity, request);
        if (!java.util.Objects.equals(estadoAnterior, entity.getEstado())) {
            exigirPermisoEstado(entity.getEstado());
            validarTransicion(estadoAnterior, entity.getEstado());
        }

        Reunion saved = repository.save(entity);
        logAudit("ACTUALIZAR", "Reunion", saved.getId(),
            "Reunión actualizada: [" + saved.getCodigo() + "] " + saved.getTitulo()
            + (estadoAnterior != null && !estadoAnterior.equals(saved.getEstado())
                ? " [estado: " + estadoAnterior + " -> " + saved.getEstado() + "]" : ""));
        if (!java.util.Objects.equals(estadoAnterior, saved.getEstado())) {
            notificarCambioEstado(saved);
        } else {
            programarNotificacionParticipantes(saved, "reunion_actualizada", "Reuni\u00f3n actualizada: " + saved.getTitulo(),
                "La reuni\u00f3n " + formatoReunion(saved) + " fue actualizada.");
        }
        return toResponse(saved);
    }

    @Transactional
    public ReunionResponse cambiarEstado(Long id, String nuevoEstado) {
        Reunion entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Reunión no encontrada con id: " + id));
        Usuario usuario = usuarioActual();
        Set<String> unidades = usuario == null ? Set.of() : unidadesDirigidasPor(usuario);
        if (!puedeGestionarEstado(entity, usuario, unidades)) {
            throw new AccessDeniedException("No tienes permisos para cambiar el estado de esta reunión");
        }

        String estadoAnterior = entity.getEstado();
        String estado = normalizarEstado(nuevoEstado);
        exigirPermisoEstado(estado);
        if (java.util.Objects.equals(estadoAnterior, estado)) return toResponse(entity, usuario, unidades);
        validarTransicion(estadoAnterior, estado);
        entity.setEstado(estado);

        Reunion saved = repository.save(entity);
        logAudit("ACTUALIZAR", "Reunion", saved.getId(),
            "Estado cambiado: [" + estadoAnterior + " -> " + nuevoEstado + "] " + saved.getTitulo());
        notificarCambioEstado(saved);
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReunionResponse duplicar(Long id) {
        Reunion original = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Reunión no encontrada con id: " + id));
        exigirGestion(original);

        Reunion duplicada = new Reunion();
        duplicada.setCodigo(generateCodigo());
        duplicada.setFecha(original.getFecha());
        duplicada.setFechaIso(original.getFechaIso());
        duplicada.setHoraInicio(original.getHoraInicio());
        duplicada.setHoraFin(original.getHoraFin());
        duplicada.setTitulo(original.getTitulo() + " (Copia)");
        duplicada.setSubtitulo(original.getSubtitulo());
        duplicada.setDescripcion(original.getDescripcion());
        duplicada.setObjetivo(original.getObjetivo());
        duplicada.setTipoReunion(original.getTipoReunion());
        duplicada.setArea(original.getArea());
        duplicada.setParticipantesUsuarios(new LinkedHashSet<>(original.getParticipantesUsuarios()));
        duplicada.setLugar(original.getLugar());
        duplicada.setModalidad(original.getModalidad());
        duplicada.setEnlaceVirtual(original.getEnlaceVirtual());
        duplicada.setSala(original.getSala());
        duplicada.setUnidadResponsable(original.getUnidadResponsable());
        duplicada.setOrganizacion(original.getOrganizacion());
        duplicada.setEstado("programada");
        duplicada.setParticipantes(original.getParticipantes());
        duplicada.setParticipantesNombres(original.getParticipantesNombres());
        duplicada.setObservaciones(original.getObservaciones());
        Usuario creador = usuarioActual();
        if (creador == null) throw new AccessDeniedException("No se pudo identificar al creador de la reunión");
        duplicada.setOrganizadorUsuario(creador);
        duplicada.setOrganizador(nombreCompleto(creador));
        participantesIniciales(duplicada, creador);
        duplicada.setUnidades(new LinkedHashSet<>(original.getUnidades()));
        sincronizarUnidades(duplicada, null);

        Reunion saved = repository.save(duplicada);
        logAudit("CREAR", "Reunion", saved.getId(),
            "Reunión duplicada desde [" + original.getCodigo() + "] como [" + saved.getCodigo() + "]");
        return toResponse(saved);
    }

    @Transactional
    public ReunionResponse reprogramar(Long id, ReunionRequest request) {
        Reunion entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Reunión no encontrada con id: " + id));
        exigirGestion(entity);

        String fechaAnterior = entity.getFecha().toString();
        String horaAnterior = entity.getHoraInicio().toString();

        if (request.getFecha() != null) entity.setFecha(LocalDate.parse(request.getFecha()));
        if (request.getFechaIso() != null) entity.setFechaIso(request.getFechaIso());
        if (request.getHoraInicio() != null) entity.setHoraInicio(horaValida(request.getHoraInicio(), "inicio"));
        if (request.getHoraFin() != null) entity.setHoraFin(horaValida(request.getHoraFin(), "fin"));
        validarHorario(entity);
        if (request.getLugar() != null) entity.setLugar(request.getLugar());
        if (request.getSala() != null) entity.setSala(request.getSala());
        if (request.getEnlaceVirtual() != null) entity.setEnlaceVirtual(request.getEnlaceVirtual());

        Reunion saved = repository.save(entity);
        logAudit("ACTUALIZAR", "Reunion", saved.getId(),
            "Reunión reprogramada: [" + fechaAnterior + " " + horaAnterior + " -> "
            + saved.getFecha() + " " + saved.getHoraInicio() + "]");
        programarNotificacionParticipantes(saved, "reunion_reprogramada", "Reuni\u00f3n reprogramada: " + saved.getTitulo(),
            "La reuni\u00f3n fue reprogramada para " + saved.getFecha() + " a las " + saved.getHoraInicio() + ".");
        return toResponse(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        Reunion entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Reunión no encontrada con id: " + id));
        exigirGestion(entity);
        repository.delete(entity);
        logAudit("ELIMINAR", "Reunion", id,
            "Reunión eliminada: [" + entity.getCodigo() + "] " + entity.getTitulo());
    }

    private void applyRequest(Reunion entity, ReunionRequest request) {
        if (request.getTitulo() != null) entity.setTitulo(request.getTitulo().trim());
        if (request.getSubtitulo() != null) entity.setSubtitulo(request.getSubtitulo().trim());
        if (request.getDescripcion() != null) entity.setDescripcion(request.getDescripcion().trim());
        if (request.getObjetivo() != null) entity.setObjetivo(request.getObjetivo().trim());
        if (request.getFecha() != null) {
            entity.setFecha(LocalDate.parse(request.getFecha()));
            if (request.getFechaIso() != null) entity.setFechaIso(request.getFechaIso());
            else entity.setFechaIso(request.getFecha() + "T00:00");
        }
        if (request.getHoraInicio() != null) entity.setHoraInicio(horaValida(request.getHoraInicio(), "inicio"));
        if (request.getHoraFin() != null) entity.setHoraFin(horaValida(request.getHoraFin(), "fin"));
        validarHorario(entity);
        if (request.getTipoReunion() != null) entity.setTipoReunion(request.getTipoReunion());
        if (request.getArea() != null) entity.setArea(request.getArea().trim());
        if (request.getOrganizador() != null) {
            entity.setOrganizador(request.getOrganizador().trim());
            resolverIdentidad(request.getOrganizador()).ifPresent(entity::setOrganizadorUsuario);
        }
        if (request.getOrganizadorUsuarioId() != null) {
            Usuario organizador = usuarioRepository.findById(request.getOrganizadorUsuarioId())
                    .orElseThrow(() -> new IllegalArgumentException("El organizador seleccionado no existe"));
            entity.setOrganizadorUsuario(organizador);
            entity.setOrganizador(nombreCompleto(organizador));
        }
        if (request.getLugar() != null) entity.setLugar(request.getLugar().trim());
        if (request.getModalidad() != null) entity.setModalidad(request.getModalidad());
        if (request.getEnlaceVirtual() != null) entity.setEnlaceVirtual(request.getEnlaceVirtual().trim());
        if (request.getSala() != null) entity.setSala(request.getSala().trim());
        if (request.getUnidadResponsable() != null) entity.setUnidadResponsable(request.getUnidadResponsable().trim());
        if (request.getOrganizacion() != null) entity.setOrganizacion(request.getOrganizacion().trim());
        if (request.getEstado() != null) entity.setEstado(normalizarEstado(request.getEstado()));
        if (request.getObservaciones() != null) entity.setObservaciones(request.getObservaciones().trim());
        if (request.getParticipantesNombres() != null) {
            List<Usuario> identificados = request.getParticipantesNombres().stream()
                    .map(this::resolverIdentidad)
                    .flatMap(java.util.Optional::stream)
                    .toList();
            identificados.forEach(ParticipantePolicy::exigirPuedeParticipar);
            entity.setParticipantesNombres(toJsonArray(request.getParticipantesNombres()));
            entity.setParticipantes(request.getParticipantesNombres().size());
            LinkedHashSet<Usuario> normalizados = new LinkedHashSet<>(identificados);
            entity.setParticipantesUsuarios(normalizados);
        }
        if (request.getParticipanteUsuarioIds() != null) {
            List<Long> idsUnicos = request.getParticipanteUsuarioIds().stream().distinct().toList();
            List<Usuario> participantes = usuarioRepository.findAllById(idsUnicos);
            if (participantes.size() != idsUnicos.size()) {
                throw new IllegalArgumentException("Uno o más participantes seleccionados no existen");
            }
            participantes.forEach(ParticipantePolicy::exigirPuedeParticipar);
            entity.setParticipantesUsuarios(new LinkedHashSet<>(participantes));
            List<String> nombres = participantes.stream().map(this::nombreCompleto).toList();
            entity.setParticipantesNombres(toJsonArray(nombres));
            entity.setParticipantes(participantes.size());
        }
    }

    private LocalTime horaValida(String value, String campo) {
        try { return LocalTime.parse(value); }
        catch (java.time.format.DateTimeParseException failure) {
            throw new IllegalArgumentException("La hora de " + campo + " no tiene un formato válido");
        }
    }

    private void validarHorario(Reunion reunion) {
        if (reunion.getHoraInicio() == null || reunion.getHoraFin() == null
                || !reunion.getHoraFin().isAfter(reunion.getHoraInicio())) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio");
        }
    }

    private void sincronizarUnidades(Reunion reunion, ReunionRequest request) {
        var unidades = new LinkedHashMap<Long, com.sigaa.model.Unidad>();
        if (request == null || request.getUnidadIds() == null) {
            reunion.getUnidades().forEach(u -> unidades.put(u.getId(), u));
        } else {
            Set<Long> ids = new LinkedHashSet<>(request.getUnidadIds());
            if (ids.contains(null)) throw new IllegalArgumentException("La unidad seleccionada no es válida");
            var seleccionadas = unidadRepository.findAllById(ids);
            if (seleccionadas.size() != ids.size()) throw new IllegalArgumentException("Una unidad seleccionada no existe");
            seleccionadas.forEach(u -> unidades.put(u.getId(), u));
        }
        var participantes = reunion.getParticipantesUsuarios().stream().map(Usuario::getId).toList();
        if (!participantes.isEmpty()) usuarioUnidadRepository.findByUsuarioIdIn(participantes).stream()
                .map(com.sigaa.model.UsuarioUnidad::getUnidad)
                .filter(u -> u != null && Boolean.TRUE.equals(u.getActivo()))
                .forEach(u -> unidades.putIfAbsent(u.getId(), u));
        reunion.setUnidades(new LinkedHashSet<>(unidades.values()));
    }

    /** Solo al crear: la edición conserva la selección explícita existente. */
    private void participantesIniciales(Reunion reunion, Usuario creador) {
        var participantes = new LinkedHashMap<Long, Usuario>();
        reunion.getParticipantesUsuarios().forEach(usuario -> participantes.putIfAbsent(usuario.getId(), usuario));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!InstitutionalRoles.hasRole(auth, "Secretaria") && !InstitutionalRoles.hasRole(auth, "Superadmin")
                && ParticipantePolicy.puedeParticipar(creador)) {
            participantes.putIfAbsent(creador.getId(), creador);
        }
        // Conservar referencias heredadas no identificadas sin usarlas como autorización o destinatarios.
        var sinIdentificar = new LinkedHashMap<String, String>();
        for (String nombre : parseJsonArray(reunion.getParticipantesNombres())) {
            boolean identificado = participantes.values().stream().anyMatch(usuario ->
                    nombre.equalsIgnoreCase(nombreCompleto(usuario)) || nombre.equalsIgnoreCase(usuario.getCorreo()));
            if (!identificado) sinIdentificar.putIfAbsent(nombre.toLowerCase(Locale.ROOT), nombre);
        }
        List<String> nombres = new ArrayList<>(participantes.values().stream().map(this::nombreCompleto).toList());
        nombres.addAll(sinIdentificar.values());
        reunion.setParticipantesUsuarios(new LinkedHashSet<>(participantes.values()));
        reunion.setParticipantesNombres(toJsonArray(nombres));
        reunion.setParticipantes(nombres.size());
    }

    /** Centraliza la visibilidad para que todos los endpoints, incluido el
     * calendario y el panel, partan de la misma política de datos. */
    @Transactional(readOnly = true)
    public List<Reunion> reunionesVisiblesActual() {
        Usuario usuario = usuarioActual();
        if (usuario == null) return List.of();
        if (consultaGlobalActual()) return repository.findAllOrderByFechaDescHoraDesc();

        LinkedHashSet<Reunion> visibles = new LinkedHashSet<>(
            esSubdirectorActual() ? repository.findByAmbitoSubdireccion(usuario.getId())
                : repository.findByParticipanteUsuarioIdOrderByFechaDescHoraDesc(usuario.getId()));
        Set<String> unidadesDirigidas = unidadesDirigidasPor(usuario);
        if (!unidadesDirigidas.isEmpty()) {
            visibles.addAll(repository.findByUnidadResponsableInOrderByFechaDescHoraInicioDesc(unidadesDirigidas));
        }
        return visibles.stream()
            .sorted(java.util.Comparator.comparing(Reunion::getFecha,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(Reunion::getHoraInicio,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .toList();
    }

    private void exigirLectura(Reunion reunion) {
        if (!puedeVer(reunion)) {
            throw new org.springframework.security.access.AccessDeniedException("No tienes acceso a esta reunión");
        }
    }

    private void exigirGestion(Reunion reunion) {
        Usuario usuario = usuarioActual();
        if (!puedeGestionar(reunion, usuario, usuario == null ? Set.of() : unidadesDirigidasPor(usuario))) {
            throw new AccessDeniedException("No tienes permisos para modificar esta reunión");
        }
    }

    private boolean puedeGestionar(Reunion reunion, Usuario usuario, Set<String> unidades) {
        if (esAdministradorActual()) return true;
        if (usuario == null) return false;
        // La consulta global de Secretaría nunca amplía el alcance de gestión de otro cargo.
        if (esSubdirectorActual() && enAmbitoPersonal(reunion, usuario, unidades)) return true;
        return unidades.stream().anyMatch(unidad -> unidad.equalsIgnoreCase(valorSeguro(reunion.getUnidadResponsable())));
    }

    private boolean puedeGestionarEstado(Reunion reunion, Usuario usuario, Set<String> unidades) {
        return esOrganizador(reunion, usuario) || puedeGestionar(reunion, usuario, unidades);
    }

    private boolean esOrganizador(Reunion reunion, Usuario usuario) {
        return usuario != null && reunion.getOrganizadorUsuario() != null
            && usuario.getId().equals(reunion.getOrganizadorUsuario().getId());
    }

    private boolean puedeVer(Reunion reunion) {
        if (consultaGlobalActual()) return true;
        Usuario usuario = usuarioActual();
        return usuario != null && enAmbitoPersonal(reunion, usuario, unidadesDirigidasPor(usuario));
    }

    private boolean enAmbitoPersonal(Reunion reunion, Usuario usuario, Set<String> unidades) {
        if (esSubdirectorActual() && esOrganizador(reunion, usuario)) return true;
        boolean participante = reunion.getParticipantesUsuarios().stream()
            .anyMatch(participanteUsuario -> participanteUsuario.getId().equals(usuario.getId()));
        return participante || unidades.stream()
            .anyMatch(unidad -> unidad.equalsIgnoreCase(valorSeguro(reunion.getUnidadResponsable())));
    }

    private boolean consultaGlobalActual() {
        return esAdministradorActual()
            || InstitutionalRoles.hasRole(SecurityContextHolder.getContext().getAuthentication(), "Secretaria");
    }

    private Usuario usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) return null;
        return usuarioRepository.findByCorreoIgnoreCase(authentication.getName()).orElse(null);
    }

    private boolean esAdministradorActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return InstitutionalRoles.operational(authentication);
    }

    private boolean esSubdirectorActual() {
        return InstitutionalRoles.scopedSubdirector(SecurityContextHolder.getContext().getAuthentication());
    }

    private Set<String> unidadesDirigidasPor(Usuario usuario) {
        return usuarioUnidadRepository.findByUsuarioId(usuario.getId()).stream()
            .filter(membresia -> membresia.getRol() != null
                && (esSubdirectorActual() ? Boolean.TRUE.equals(membresia.getRol().getActivo())
                    : "jefe de unidad".equalsIgnoreCase(membresia.getRol().getNombre())))
            .map(membresia -> membresia.getUnidad())
            .filter(java.util.Objects::nonNull)
            .map(unidad -> unidad.getNombre())
            .filter(nombre -> nombre != null && !nombre.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean contiene(String value, String query) {
        return query.isBlank() || valorSeguro(value).toLowerCase(Locale.ROOT).contains(query);
    }

    private String valorSeguro(String value) {
        return value == null ? "" : value;
    }

    /** Las notificaciones se ejecutan tras confirmar la reunión y nunca deben
     * impedir que el usuario pueda crearla, editarla o reprogramarla. */
    private void programarNotificacionParticipantes(Reunion reunion, String evento, String titulo, String mensaje) {
        Set<Long> destinatarioIds = resolverDestinatarioIds(reunion);
        if (destinatarioIds.isEmpty()) return;
        String tituloAviso = titulo.codePointCount(0, titulo.length()) > 300
            ? titulo.substring(0, titulo.offsetByCodePoints(0, 297)) + "..." : titulo;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    for (Usuario usuario : usuarioRepository.findAllById(destinatarioIds)) {
                        try { notificationService.sendAfterCommit(evento, usuario, tituloAviso, mensaje); }
                        catch (RuntimeException failure) {
                            log.warn("No se pudo notificar la reunión id={}: {}", reunion.getId(), LogDiagnostics.describe(failure));
                        }
                    }
                } catch (RuntimeException failure) {
                    log.warn("No se pudieron resolver los destinatarios de la reunión id={}: {}",
                        reunion.getId(), LogDiagnostics.describe(failure));
                }
            }
        });
    }

    private Set<Long> resolverDestinatarioIds(Reunion reunion) {
        return reunion.getParticipantesUsuarios().stream().filter(ParticipantePolicy::puedeParticipar).map(Usuario::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void notificarCambioEstado(Reunion reunion) {
        String estado = estadoLegible(reunion.getEstado());
        String evento = "cancelada".equalsIgnoreCase(reunion.getEstado())
            ? "reunion_cancelada" : "reunion_actualizada";
        String responsable = nombreUsuarioActual();
        String titulo = "en_curso".equals(reunion.getEstado()) ? "Reunión iniciada: " : "Reunión " + estado + ": ";
        programarNotificacionParticipantes(reunion, evento, titulo + reunion.getTitulo(),
            responsable + " cambi\u00f3 el estado de la reuni\u00f3n " + formatoReunion(reunion)
                + " a " + estado + ".");
    }

    private String nombreUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String correo = auth == null ? "" : auth.getName();
        return usuarioRepository.findByCorreoIgnoreCase(correo)
            .map(usuario -> (String.join(" ",
                usuario.getNombre() == null ? "" : usuario.getNombre(),
                usuario.getApellido() == null ? "" : usuario.getApellido())).trim())
            .filter(nombre -> !nombre.isBlank())
            .orElse(correo == null || correo.isBlank() ? "Un usuario" : correo);
    }

    private String estadoLegible(String estado) {
        return estado == null || estado.isBlank() ? "actualizada" : estado.replace('_', ' ');
    }

    private String normalizarEstado(String estado) {
        String normalizado = estado == null ? "" : estado.trim().toLowerCase(Locale.ROOT)
            .replace('-', '_').replace(' ', '_');
        return switch (normalizado) {
            case "programada", "en_curso", "realizada", "cancelada" -> normalizado;
            case "completada", "finalizada", "terminada" -> "realizada";
            default -> throw new IllegalArgumentException("El estado de la reunión no es válido");
        };
    }

    private boolean tienePermiso(String accion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
            .anyMatch(authority -> ("Reuniones:" + accion).equals(authority.getAuthority()));
    }

    private void exigirPermisoEstado(String estado) {
        String accion = "cancelada".equals(estado) ? "Cancelar" : "programada".equals(estado) ? "Editar" : "Iniciar";
        if (!tienePermiso(accion)) throw new AccessDeniedException("No tienes permisos para realizar este cambio de estado");
    }

    private void validarTransicion(String anterior, String nuevo) {
        boolean permitida = switch (nuevo) {
            case "en_curso" -> "programada".equals(anterior);
            case "realizada" -> "en_curso".equals(anterior);
            case "cancelada" -> estadoAbierto(anterior);
            // Se conserva la reprogramación explícita por edición y con permiso Editar.
            case "programada" -> true;
            default -> false;
        };
        if (!permitida) throw new IllegalStateException("El estado actual de la reunión no permite realizar esta acción.");
    }

    private boolean estadoAbierto(String estado) {
        return "programada".equals(estado) || "en_curso".equals(estado);
    }

    private java.util.Optional<Usuario> resolverIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) return java.util.Optional.empty();
        List<Usuario> matches = usuarioRepository.findByIdentidadExacta(identidad.trim());
        return matches.size() == 1 ? java.util.Optional.of(matches.getFirst()) : java.util.Optional.empty();
    }

    private String nombreCompleto(Usuario usuario) {
        return String.join(" ", usuario.getNombre() == null ? "" : usuario.getNombre(),
                usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(),
                usuario.getApellido() == null ? "" : usuario.getApellido())
                .trim().replaceAll("\\s+", " ");
    }

    private String formatoReunion(Reunion reunion) {
        return "\"" + reunion.getTitulo() + "\" (" + reunion.getFecha() + " " + reunion.getHoraInicio() + ")";
    }

    private ReunionResponse toResponse(Reunion entity) {
        Usuario actual = usuarioActual();
        return toResponse(entity, actual, actual == null ? Set.of() : unidadesDirigidasPor(actual));
    }

    private List<ReunionResponse> respuestas(List<Reunion> reuniones) {
        Usuario actual = usuarioActual();
        Set<String> unidades = actual == null ? Set.of() : unidadesDirigidasPor(actual);
        return reuniones.stream().map(reunion -> toResponse(reunion, actual, unidades)).toList();
    }

    private ReunionResponse toResponse(Reunion entity, Usuario actual, Set<String> unidades) {
        ReunionResponse resp = new ReunionResponse();
        resp.setId(entity.getId());
        resp.setCodigo(entity.getCodigo());
        resp.setFecha(entity.getFecha() != null ? entity.getFecha().toString() : "");
        resp.setFechaISO(entity.getFechaIso());
        resp.setHoraInicio(entity.getHoraInicio() != null ? entity.getHoraInicio().toString() : "");
        resp.setHoraFin(entity.getHoraFin() != null ? entity.getHoraFin().toString() : "");
        resp.setTitulo(entity.getTitulo());
        resp.setSubtitulo(entity.getSubtitulo());
        resp.setDescripcion(entity.getDescripcion());
        resp.setObjetivo(entity.getObjetivo());
        resp.setTipoReunion(entity.getTipoReunion());
        resp.setArea(entity.getArea());
        resp.setOrganizador(entity.getOrganizador());
        resp.setOrganizadorUsuarioId(entity.getOrganizadorUsuario() == null ? null : entity.getOrganizadorUsuario().getId());
        resp.setLugar(entity.getLugar());
        resp.setModalidad(entity.getModalidad());
        resp.setEnlaceVirtual(entity.getEnlaceVirtual());
        resp.setSala(entity.getSala());
        resp.setUnidadResponsable(entity.getUnidadResponsable());
        resp.setOrganizacion(entity.getOrganizacion());
        resp.setUnidades(entity.getUnidades().stream().map(u -> java.util.Map.<String, Object>of("id", u.getId(), "nombre", u.getNombre())).toList());
        resp.setUnidadIds(entity.getUnidades().stream().map(com.sigaa.model.Unidad::getId).sorted().toList());
        resp.setEstado(entity.getEstado());
        Set<String> nombresAdministrativos = entity.getParticipantesUsuarios().stream()
            .filter(ParticipantePolicy::esAdministrador)
            .map(this::nombreCompleto)
            .map(nombre -> nombre.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        List<Usuario> participantesPermitidos = entity.getParticipantesUsuarios().stream()
            .filter(usuario -> !ParticipantePolicy.esAdministrador(usuario))
            .toList();
        List<String> nombresPermitidos = parseJsonArray(entity.getParticipantesNombres()).stream()
            .filter(nombre -> !nombresAdministrativos.contains(nombre.trim().toLowerCase(Locale.ROOT)))
            .toList();
        resp.setParticipantes(nombresPermitidos.size());
        resp.setParticipantesNombres(nombresPermitidos);
        resp.setParticipanteUsuarioIds(participantesPermitidos.stream().map(Usuario::getId).toList());
        resp.setObservaciones(entity.getObservaciones());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        boolean gestion = puedeGestionar(entity, actual, unidades);
        boolean gestionEstado = esOrganizador(entity, actual) || gestion;
        resp.setPuedeIniciar(gestionEstado && tienePermiso("Iniciar") && "programada".equals(entity.getEstado()));
        resp.setPuedeFinalizar(gestionEstado && tienePermiso("Iniciar") && "en_curso".equals(entity.getEstado()));
        resp.setPuedeCancelar(gestionEstado && tienePermiso("Cancelar")
                && estadoAbierto(entity.getEstado()));
        resp.setPuedeEditar(gestion && (tienePermiso("Editar") || tienePermiso("Gestionar participantes")));
        resp.setPuedeEliminar(gestion && tienePermiso("Eliminar"));
        return resp;
    }

    private String generateCodigo() {
        repository.bloquearGeneracionCodigo();
        long ultimo = repository.findMaxCodigoNumber();
        if (ultimo == Long.MAX_VALUE) throw new IllegalStateException("Se agotó la numeración de reuniones");
        return "RUN-" + String.format(Locale.ROOT, "%04d", ultimo + 1);
    }

    private String toJsonArray(List<String> list) {
        try { return objectMapper.writeValueAsString(list == null ? List.of() : list); }
        catch (JsonProcessingException failure) { throw new IllegalArgumentException("Los participantes no son válidos."); }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return new ArrayList<>();
        try {
            String[] nombres = objectMapper.readValue(json, String[].class);
            if (nombres == null) return List.of();
            return Arrays.stream(nombres)
                .filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).toList();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new ArrayList<>();
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
