package com.sigaa.service;

import com.sigaa.dto.NotaCreateRequest;
import com.sigaa.dto.NotaRelacionRequest;
import com.sigaa.dto.NotaUpdateRequest;
import com.sigaa.dto.NotaVistaPreviaRequest;
import com.sigaa.dto.NotaSeguimientoRequest;
import com.sigaa.dto.OrganizationDepartmentResponse;
import com.sigaa.model.Nota;
import com.sigaa.model.NotaNumero;
import com.sigaa.model.Organization;
import com.sigaa.model.OrganizationDepartment;
import com.sigaa.model.Usuario;
import com.sigaa.model.NotaSeguimientoEstado;
import com.sigaa.repository.NotaRepository;
import com.sigaa.repository.OrganizationRepository;
import com.sigaa.repository.OrganizationDepartmentRepository;
import com.sigaa.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NotaService {
    public static final String BORRADOR = "BORRADOR";
    public static final String EN_SEGUIMIENTO = "EN_SEGUIMIENTO";
    public static final String FIRMADA = "FIRMADA";
    public static final String CERRADA = "CERRADA";
    public static final String ANULADA = "ANULADA";
    private static final ZoneId PANAMA = ZoneId.of("America/Panama");

    private final NotaRepository notaRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrganizationRepository organizationRepository;
    private final NotaNumeroService numeroService;
    private final NotaRelacionService relacionService;
    private final NotaTrazabilidadService trazabilidad;
    private final InternalNotificationService notificaciones;
    private final OrganizationDepartmentRepository departmentRepository;

    public NotaService(NotaRepository notaRepository, UsuarioRepository usuarioRepository,
                       OrganizationRepository organizationRepository,
                       NotaNumeroService numeroService,
                       NotaRelacionService relacionService, NotaTrazabilidadService trazabilidad,
                       InternalNotificationService notificaciones,
                       OrganizationDepartmentRepository departmentRepository) {
        this.notaRepository = notaRepository;
        this.usuarioRepository = usuarioRepository;
        this.organizationRepository = organizationRepository;
        this.numeroService = numeroService;
        this.relacionService = relacionService;
        this.trazabilidad = trazabilidad;
        this.notificaciones = notificaciones;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Nota> listar() { return notaRepository.findAllByOrderByCreatedAtDesc(); }

    @Transactional(readOnly = true)
    public Listado listado(String query, String numero, String destinatario, String estado,
                          LocalDate anioDesde, LocalDate anioHasta, LocalDate fechaDesde, LocalDate fechaHasta,
                          String responsable, int page, int size, Long usuarioId, boolean admin) {
        int pagina = Math.max(page, 0);
        int tamano = Math.min(Math.max(size, 1), 50);
        String termino = limpiar(query), numeroFiltro = limpiar(numero), destinatarioFiltro = limpiar(destinatario);
        String responsableFiltro = limpiar(responsable);
        Map<String, Long> resumen = new LinkedHashMap<>();
        notaRepository.resumirFiltradas(termino, patron(termino), numeroFiltro, patron(numeroFiltro),
            destinatarioFiltro, patron(destinatarioFiltro), estado, anioDesde, anioHasta, fechaDesde, fechaHasta,
            responsableFiltro, patron(responsableFiltro), usuarioId, admin)
            .forEach(fila -> resumen.put((String) fila[0], ((Number) fila[1]).longValue()));
        long total = resumen.values().stream().mapToLong(Long::longValue).sum();
        int totalPaginas = (int) Math.ceil((double) total / tamano);
        pagina = Math.min(pagina, Math.max(totalPaginas - 1, 0));
        List<Nota> notas = notaRepository.listarFiltradas(termino, patron(termino), numeroFiltro, patron(numeroFiltro),
            destinatarioFiltro, patron(destinatarioFiltro), estado, anioDesde, anioHasta, fechaDesde, fechaHasta,
            responsableFiltro, patron(responsableFiltro), usuarioId, admin, PageRequest.of(pagina, tamano));
        return new Listado(notas, resumen, total, pagina, tamano, totalPaginas);
    }

    private String patron(String valor) {
        return "%" + valor.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public record Listado(List<Nota> notas, Map<String, Long> resumen, long total, int page, int size, int totalPages) {}

    @Transactional(readOnly = true)
    public Nota obtener(Long id) {
        return notaRepository.findById(id).orElseThrow(() -> new NoSuchElementException("La nota no existe."));
    }

    @Transactional(readOnly = true)
    public List<OrganizationDepartmentResponse> departamentosActivos(Long institucionId) {
        institucionActiva(institucionId, true);
        return departmentRepository.findByOrganizationIdAndActivoTrueOrderByNombreAsc(institucionId).stream()
            .map(item -> new OrganizationDepartmentResponse(item.getId(), item.getNombre(),
                item.getDirector(), item.getDirectorCargo(), item.isActivo())).toList();
    }

    @Transactional(readOnly = true)
    public Page<Nota> buscar(String query, Long excluirId, int page, int size) {
        String termino = query == null ? "" : query.trim();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return notaRepository.buscarRelacionables(termino, excluirId, pageable);
    }

    @Transactional(readOnly = true)
    public Nota prepararVistaPrevia(NotaVistaPreviaRequest request, Usuario actual) {
        Nota original = request.notaId() == null ? null : obtener(request.notaId());
        // Copia transitoria: abrir la vista previa nunca modifica datos persistidos.
        Nota copia = new Nota();
        boolean editable = original == null || BORRADOR.equals(original.getEstado());
        copia.setEstado(original == null ? BORRADOR : original.getEstado());
        copia.setNumero(original != null ? original.getNumero() : request.numeroId() == null ? null : numeroService.obtener(request.numeroId()));
        copia.setFechaOficial(editable && request.fechaOficial() != null ? request.fechaOficial()
            : original != null ? original.getFechaOficial() : LocalDate.now(PANAMA));
        if (copia.getNumero() != null && copia.getNumero().getConsecutivo() != null
                && copia.getFechaOficial().getYear() != copia.getNumero().getAnio()) {
            throw new IllegalArgumentException("El año de la fecha debe coincidir con el consecutivo reservado.");
        }
        copia.setAsunto(editable && request.asunto() != null ? limpiar(request.asunto()) : original == null ? "" : original.getAsunto());
        copia.setDestinatarioNombre(editable && request.destinatarioNombre() != null ? limpiar(request.destinatarioNombre()) : original == null ? "" : original.getDestinatarioNombre());
        copia.setDestinatarioCargo(editable && request.destinatarioCargo() != null ? limpiar(request.destinatarioCargo()) : original == null ? "" : original.getDestinatarioCargo());
        copia.setContenido(editable && request.contenido() != null ? request.contenido() : original == null ? "" : original.getContenido());
        Organization institucion = original == null ? null : original.getInstitucion();
        if (editable) {
            if (request.institucionId() == null && Boolean.TRUE.equals(request.institucionDefinida())) institucion = null;
            else if (request.institucionId() != null && !(Boolean.TRUE.equals(request.institucionDefinida())
                    && java.util.Objects.equals(request.institucionId(), idInstitucion(institucion)))) {
                institucion = institucionActiva(request.institucionId(), false);
            }
        }
        copia.setInstitucion(institucion);
        copia.setDestino(institucion == null ? original != null && original.getInstitucion() == null ? original.getDestino() : ""
            : original != null && java.util.Objects.equals(idInstitucion(original.getInstitucion()), institucion.getId())
                ? original.getInstitucionNombreGuardado() : institucion.getNombre());
        copia.setCreadoPor(original == null ? actual : original.getCreadoPor());
        if (original != null && original.getFirmanteNombre() != null && !original.getFirmanteNombre().isBlank()) {
            copia.setFirmanteNombre(original.getFirmanteNombre()); copia.setFirmanteCargo(original.getFirmanteCargo());
            copia.setFirmanteInstitucion(original.getFirmanteInstitucion());
        } else {
            List<Usuario> directoras = usuarioRepository.findActiveDirectors();
            if (directoras.size() == 1) {
                Usuario directora = directoras.get(0);
                copia.setFirmanteNombre(nombreCompleto(directora)); copia.setFirmanteCargo(directora.getCargo());
                copia.setFirmanteInstitucion(directora.getInstitucion());
            }
        }
        return copia;
    }

    @Transactional(readOnly = true)
    public Page<Nota> buscarAutorizadas(String query, Long excluirId, int page, int size, Long usuarioId, boolean admin) {
        return notaRepository.buscarAutorizadas(query == null ? "" : query.trim(), excluirId, usuarioId, admin,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
    }

    @Transactional
    public Nota crear(NotaCreateRequest request, MultipartFile pdf, Usuario creador,
                      boolean puedeRelacionar) {
        if (pdf != null && !pdf.isEmpty()) {
            throw new IllegalArgumentException("El PDF se adjunta después de registrar la firma del receptor, no al crear la nota.");
        }
        if (!request.getRelaciones().isEmpty() && !puedeRelacionar) {
            throw new AccessDeniedException("No tiene permiso para relacionar notas.");
        }
        NotaNumeroService.ReservaConsumida reserva = numeroService.consumirReserva(request.getNumeroId(), creador);
        NotaNumero numero = reserva.numero();
        Usuario seguimiento = usuarioActivo(request.getSeguimientoUsuarioId(), "La persona de seguimiento");
        Organization institucion = institucionActiva(request.getInstitucionId(), false);
        OrganizationDepartment departamento = departamentoActivo(request.getDepartamentoId(), institucion);
        Nota nota = new Nota();
        nota.setNumero(numero);
        nota.setAsunto(limpiar(request.getAsunto()));
        nota.setFechaOficial(LocalDate.now(PANAMA));
        nota.setDestinatarioNombre(limpiar(request.getDestinatarioNombre() == null && departamento != null
            ? departamento.getDirector() : request.getDestinatarioNombre()));
        nota.setDestinatarioCargo(limpiar(request.getDestinatarioCargo() == null && departamento != null
            ? departamento.getDirectorCargo() : request.getDestinatarioCargo()));
        nota.setInstitucion(institucion);
        nota.setDepartamento(departamento);
        nota.setDepartamentoNombre(departamento == null ? null : departamento.getNombre());
        nota.setDestino(institucion == null ? limpiar(request.getDestino()) : institucion.getNombre());
        nota.setContenido(NotaWordService.normalizarContenido(request.getContenido() == null ? request.getDescripcion() : request.getContenido()));
        nota.setDescripcion(limpiarDescripcion(request.getDescripcion()));
        nota.setSensitiva(request.isSensitiva());
        nota.setCreadoPor(creador);
        nota.setSeguimientoUsuario(seguimiento);
        nota.setEstado(BORRADOR);
        Nota guardada = notaRepository.saveAndFlush(nota);
        trazabilidad.historial(guardada, reserva.usuario(), "RESERVAR_NUMERO", null, null,
            "Se reservó el número " + numero.getNumero() + " antes de crear esta nota.", reserva.desde());
        trazabilidad.historial(guardada, creador, "OCUPAR_NUMERO", null, BORRADOR,
            "El número " + numero.getNumero() + " quedó ocupado y asociado permanentemente al borrador.");
        trazabilidad.historial(guardada, creador, "CREAR_BORRADOR", null, BORRADOR,
            "Borrador creado con fecha oficial " + guardada.getFechaOficial() + ".");
        trazabilidad.audit("CREAR_BORRADOR", guardada.getId(), "Borrador de nota " + numero.getNumero() + " creado.");
        if (!guardada.getDescripcion().isBlank()) {
            trazabilidad.historial(guardada, creador, "SEGUIMIENTO_INICIAL", BORRADOR, BORRADOR, guardada.getDescripcion());
        }

        for (NotaRelacionRequest relacion : request.getRelaciones()) {
            relacionService.crear(guardada.getId(), relacion, creador);
        }
        notificarAsignacion(guardada);
        return guardada;
    }

    @Transactional
    public Nota actualizar(Long id, NotaUpdateRequest request, Usuario actual) {
        Nota nota = notaRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        LocalDate fechaAnterior = nota.getFechaOficial();
        boolean cambioFecha = request.getFechaOficial() != null
            && !request.getFechaOficial().equals(fechaAnterior);
        if (cambioFecha && nota.getNumero() != null && nota.getNumero().getConsecutivo() != null
                && request.getFechaOficial().getYear() != nota.getNumero().getAnio()) {
            throw new IllegalArgumentException("El año de la fecha debe coincidir con el consecutivo reservado.");
        }
        if (cambioFecha && (!BORRADOR.equals(nota.getEstado()) || nota.getOficializadaAt() != null
                || nota.getOficializadaPor() != null)) {
            throw new IllegalStateException("La fecha solo puede modificarse antes de oficializar la nota.");
        }
        if (!BORRADOR.equals(nota.getEstado()) || nota.getOficializadaAt() != null || nota.getOficializadaPor() != null) {
            throw new IllegalStateException("Una nota oficializada no puede modificarse. Solo se pueden editar borradores.");
        }
        Usuario seguimiento = usuarioActivo(request.getSeguimientoUsuarioId(), "La persona de seguimiento");
        Usuario responsableAnterior = nota.getSeguimientoUsuario();
        boolean cambioSeguimiento = !responsableAnterior.getId().equals(seguimiento.getId());
        Organization institucion = institucionParaActualizar(nota, request);
        OrganizationDepartment departamento = departamentoParaActualizar(nota, request, institucion);
        boolean cambioDepartamento = !java.util.Objects.equals(idDepartamento(nota.getDepartamento()), idDepartamento(departamento));
        String nombreDepartamento = departamento == null ? null
            : !cambioDepartamento && nota.getDepartamentoNombre() != null ? nota.getDepartamentoNombre() : departamento.getNombre();
        String asunto = limpiar(request.getAsunto());
        String destinatarioNombre = request.getDestinatarioNombre() == null
            ? cambioDepartamento && departamento != null ? limpiar(departamento.getDirector()) : nota.getDestinatarioNombre()
            : limpiar(request.getDestinatarioNombre());
        String destinatarioCargo = request.getDestinatarioCargo() == null
            ? cambioDepartamento && departamento != null ? limpiar(departamento.getDirectorCargo()) : nota.getDestinatarioCargo()
            : limpiar(request.getDestinatarioCargo());
        String destino = institucion == null ? limpiar(request.getDestino())
            : java.util.Objects.equals(idInstitucion(nota.getInstitucion()), institucion.getId())
                && nota.getDestino() != null && !nota.getDestino().isBlank() ? nota.getDestino() : institucion.getNombre();
        String contenido = request.getContenido() == null
            ? nota.getContenido() : NotaWordService.normalizarContenido(request.getContenido());
        boolean cambioDatos = !nota.getAsunto().equals(asunto)
            || !nota.getDestino().equals(destino)
            || !nota.getDestinatarioNombre().equals(destinatarioNombre)
            || !nota.getDestinatarioCargo().equals(destinatarioCargo)
            || !java.util.Objects.equals(nota.getInstitucion() == null ? null : nota.getInstitucion().getId(),
                institucion == null ? null : institucion.getId())
            || cambioDepartamento
            || !java.util.Objects.equals(nota.getDepartamentoNombre(), nombreDepartamento)
            || !nota.getContenido().equals(contenido)
            || nota.isSensitiva() != request.isSensitiva();
        nota.setAsunto(asunto);
        nota.setDestino(destino);
        nota.setDestinatarioNombre(destinatarioNombre);
        nota.setDestinatarioCargo(destinatarioCargo);
        nota.setInstitucion(institucion);
        nota.setDepartamento(departamento);
        nota.setDepartamentoNombre(nombreDepartamento);
        nota.setContenido(contenido);
        nota.setSensitiva(request.isSensitiva());
        nota.setSeguimientoUsuario(seguimiento);
        if (cambioFecha) nota.setFechaOficial(request.getFechaOficial());
        Nota guardada = notaRepository.save(nota);
        if (cambioFecha) {
            String detalle = "Fecha de la nota modificada: " + fechaAnterior + " → " + guardada.getFechaOficial() + ".";
            trazabilidad.historial(guardada, actual, "CAMBIAR_FECHA", nota.getEstado(), nota.getEstado(), detalle);
            trazabilidad.audit("CAMBIAR_FECHA", id, detalle);
        }
        if (cambioDatos) {
            trazabilidad.historial(guardada, actual, "ACTUALIZAR", nota.getEstado(), nota.getEstado(),
                "Se actualizó el mismo borrador.");
        }
        if (cambioDatos) {
            trazabilidad.audit("ACTUALIZAR", id, "Nota " + guardada.getNumero().getNumero() + " actualizada.");
        }
        if (cambioSeguimiento) {
            String detalle = "Responsable de seguimiento: " + nombreCompleto(responsableAnterior) + " (usuario "
                + responsableAnterior.getId() + ") → " + nombreCompleto(seguimiento) + " (usuario " + seguimiento.getId() + ").";
            trazabilidad.historial(guardada, actual, "CAMBIAR_RESPONSABLE", nota.getEstado(), nota.getEstado(), detalle);
            trazabilidad.audit("CAMBIAR_RESPONSABLE", id, detalle);
            notificarAsignacion(guardada);
        }
        if (!limpiarDescripcion(request.getDescripcion()).isBlank()) {
            trazabilidad.historial(guardada, actual, "SEGUIMIENTO", nota.getEstado(), nota.getEstado(), limpiarDescripcion(request.getDescripcion()));
            trazabilidad.audit("SEGUIMIENTO", id, "Se agregó un comentario al borrador de nota " + guardada.getNumero().getNumero() + ".");
        }
        return guardada;
    }

    @Transactional
    public Nota oficializar(Long id, Usuario actual) {
        Nota nota = notaRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        if (!BORRADOR.equals(nota.getEstado())) {
            throw new IllegalStateException("Solo una nota en estado Borrador puede oficializarse.");
        }
        validarParaOficializar(nota);
        Usuario directora = directoraActiva();
        nota.setFirmante(directora);
        nota.setFirmanteNombre(nombreCompleto(directora));
        nota.setFirmanteCargo(limpiar(directora.getCargo()));
        nota.setFirmanteInstitucion(limpiar(directora.getInstitucion()));
        nota.setOficializadaPor(actual);
        nota.setOficializadaAt(LocalDateTime.now(PANAMA));
        nota.setEstado(EN_SEGUIMIENTO);
        Nota guardada = notaRepository.save(nota);
        String detalle = "Nota oficializada con el número " + nota.getNumero().getNumero()
            + " y firmante " + nota.getFirmanteNombre() + ", " + nota.getFirmanteCargo()
            + ", " + nota.getFirmanteInstitucion() + ".";
        trazabilidad.historial(guardada, actual, "OFICIALIZAR", BORRADOR, EN_SEGUIMIENTO, detalle);
        trazabilidad.audit("OFICIALIZAR", id, detalle);
        return guardada;
    }

    @Transactional
    public Nota firmar(Long id, Usuario actual) {
        Nota nota = notaRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        if (!EN_SEGUIMIENTO.equals(nota.getEstado())) {
            throw new IllegalStateException("Solo una nota en seguimiento puede marcarse como firmada.");
        }
        nota.setEstado(FIRMADA);
        nota.setFirmadaPor(actual);
        nota.setFirmadaAt(LocalDateTime.now());
        Nota guardada = notaRepository.save(nota);
        trazabilidad.historial(guardada, actual, "FIRMAR", EN_SEGUIMIENTO, FIRMADA,
            "Se registró la firma de la directora o director. La nota queda pendiente de entrega.");
        trazabilidad.audit("FIRMAR", id, "Nota " + guardada.getNumero().getNumero() + " firmada.");
        notificarAvance(guardada, actual, "Firma de la directora o director registrada; pendiente de entrega.");
        return guardada;
    }

    @Transactional
    public Nota seguimiento(Long id, NotaSeguimientoRequest request, Usuario actual,
                            boolean puedeComentar, Set<NotaSeguimientoEstado> estadosPermitidos) {
        Nota nota = notaRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        if (!FIRMADA.equals(nota.getEstado())) {
            throw new IllegalStateException("El seguimiento se registra después de la firma de dirección, mientras la nota permanezca abierta.");
        }
        String codigo = limpiar(request.estado());
        NotaSeguimientoEstado estado = codigo.isBlank() ? null : NotaSeguimientoEstado.porAccion(codigo)
            .orElseThrow(() -> new IllegalArgumentException("El estado de seguimiento no es válido."));
        if (estado == null ? !puedeComentar : !estadosPermitidos.contains(estado)) {
            throw new AccessDeniedException("No tiene permiso para registrar esta acción de seguimiento.");
        }
        String comentario = limpiarDescripcion(request.comentario());
        if (!comentario.isBlank() && !puedeComentar) {
            throw new AccessDeniedException("No tiene permiso para agregar comentarios de seguimiento.");
        }
        if (comentario.length() > 5000 || (!comentario.isBlank() && comentario.length() < 3)
                || (estado == null && comentario.isBlank())) {
            throw new IllegalArgumentException("Escribe un comentario de entre 3 y 5000 caracteres.");
        }
        NotaSeguimientoEstado anterior = trazabilidad.seguimientoActual(nota).estado();
        if (estado == anterior && comentario.isBlank()) return nota;
        String accion = estado != null && estado != anterior ? estado.name() : "SEGUIMIENTO";
        String detalle = estado != null && estado != anterior
            ? "Seguimiento: " + (anterior == null ? "Sin registro" : anterior.texto()) + " → " + estado.texto() + "."
                + (comentario.isBlank() ? "" : "\n" + comentario)
            : comentario;
        trazabilidad.historial(nota, actual, accion, nota.getEstado(), nota.getEstado(), detalle);
        trazabilidad.audit(accion, id, detalle);
        notificarAvance(nota, actual, detalle);
        return nota;
    }

    @Transactional
    public Nota cerrar(Long id, Usuario actual) {
        Nota nota = notaRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        if (!FIRMADA.equals(nota.getEstado())) {
            throw new IllegalStateException("La nota debe estar firmada antes de poder cerrarse.");
        }
        nota.setEstado(CERRADA);
        nota.setCerradaPor(actual);
        nota.setCerradaAt(LocalDateTime.now());
        Nota guardada = notaRepository.save(nota);
        trazabilidad.historial(guardada, actual, "CERRAR", FIRMADA, CERRADA, "La nota fue cerrada.");
        trazabilidad.audit("CERRAR", id, "Nota " + guardada.getNumero().getNumero() + " cerrada.");
        return guardada;
    }

    @Transactional
    public Nota anular(Long id, String motivo, Usuario actual) {
        Nota nota = notaRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("La nota no existe."));
        if (ANULADA.equals(nota.getEstado())) {
            throw new IllegalStateException("La nota ya se encuentra anulada.");
        }
        String motivoLimpio = limpiarDescripcion(motivo);
        if (motivoLimpio.length() < 5) throw new IllegalArgumentException("Debes indicar un motivo de anulación válido.");
        String estadoAnterior = nota.getEstado();
        nota.setEstado(ANULADA);
        nota.setAnuladaPor(actual);
        nota.setAnuladaAt(LocalDateTime.now());
        nota.setAnulacionMotivo(motivoLimpio);
        Nota guardada = notaRepository.save(nota);
        trazabilidad.historial(guardada, actual, "ANULAR", estadoAnterior, ANULADA,
            "Motivo de anulación: " + motivoLimpio);
        trazabilidad.audit("ANULAR", id, "Nota " + guardada.getNumero().getNumero() + " anulada. Motivo: " + motivoLimpio);
        notificarAvance(guardada, actual, "Nota anulada. Motivo: " + motivoLimpio);
        return guardada;
    }

    /** El aviso interno participa en la transacción: solo es visible si también se confirma la nota. */
    private void notificarAsignacion(Nota nota) {
        String numero = NotaWordService.numeroInstitucional(nota);
        notificaciones.send("nota_asignada", nota.getSeguimientoUsuario(), "Seguimiento asignado: " + numero,
            "Se te asignó el seguimiento de la nota " + numero + " (registro " + nota.getId()
                + "). Asunto: " + nota.getAsunto() + ".");
    }

    private void notificarAvance(Nota nota, Usuario actual, String detalle) {
        Usuario responsable = nota.getSeguimientoUsuario();
        if (responsable == null || responsable.getId().equals(actual.getId())) return;
        String numero = NotaWordService.numeroInstitucional(nota);
        notificaciones.send("nota_seguimiento", responsable, "Actualización de nota: " + numero,
            "Nota " + numero + " (registro " + nota.getId() + "). Por " + nombreCompleto(actual) + ": " + detalle);
    }

    private Usuario usuarioActivo(Long id, String etiqueta) {
        return usuarioRepository.findById(id)
            .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()) && !Boolean.TRUE.equals(usuario.getBloqueado()))
            .orElseThrow(() -> new IllegalArgumentException(etiqueta + " no existe o no está activa."));
    }

    private Organization institucionActiva(Long id, boolean requerida) {
        if (id == null) {
            if (requerida) throw new IllegalArgumentException("Debes seleccionar una institución activa.");
            return null;
        }
        return organizationRepository.findById(id)
            .filter(item -> Boolean.TRUE.equals(item.getActivo()))
            .orElseThrow(() -> new IllegalArgumentException("La institución seleccionada no existe o no está activa."));
    }

    private Organization institucionParaActualizar(Nota nota, NotaUpdateRequest request) {
        if (!request.isDepartamentoDefinido()) {
            return request.getInstitucionId() == null ? nota.getInstitucion() : institucionActiva(request.getInstitucionId(), true);
        }
        if (request.getInstitucionId() == null) return null;
        // Mantener una referencia histórica no equivale a seleccionar una institución inactiva nueva.
        return java.util.Objects.equals(request.getInstitucionId(), idInstitucion(nota.getInstitucion()))
            ? nota.getInstitucion() : institucionActiva(request.getInstitucionId(), true);
    }

    private OrganizationDepartment departamentoParaActualizar(Nota nota, NotaUpdateRequest request, Organization institucion) {
        if (!request.isDepartamentoDefinido()) {
            return java.util.Objects.equals(idInstitucion(nota.getInstitucion()), idInstitucion(institucion))
                ? nota.getDepartamento() : null;
        }
        if (request.getDepartamentoId() == null) return null;
        if (java.util.Objects.equals(idInstitucion(nota.getInstitucion()), idInstitucion(institucion))
                && java.util.Objects.equals(request.getDepartamentoId(), idDepartamento(nota.getDepartamento()))) {
            return nota.getDepartamento();
        }
        return departamentoActivo(request.getDepartamentoId(), institucion);
    }

    private OrganizationDepartment departamentoActivo(Long departamentoId, Organization institucion) {
        if (departamentoId == null) return null;
        if (institucion == null || !Boolean.TRUE.equals(institucion.getActivo())) {
            throw new IllegalArgumentException("Debes seleccionar una institución activa para elegir un departamento.");
        }
        return departmentRepository.findByIdAndOrganizationId(departamentoId, institucion.getId())
            .filter(OrganizationDepartment::isActivo)
            .orElseThrow(() -> new IllegalArgumentException("El departamento no existe, está inactivo o no pertenece a la institución seleccionada."));
    }

    private Long idInstitucion(Organization institucion) { return institucion == null ? null : institucion.getId(); }
    private Long idDepartamento(OrganizationDepartment departamento) { return departamento == null ? null : departamento.getId(); }

    private Usuario directoraActiva() {
        List<Usuario> directoras = usuarioRepository.findActiveDirectors();
        if (directoras.isEmpty()) {
            throw new IllegalStateException("No existe un Director o Directora activo configurado en el sistema. Configure la persona responsable antes de finalizar la nota.");
        }
        if (directoras.size() > 1) {
            throw new IllegalStateException("Existe más de un Director o Directora activo. Debe quedar una sola persona activa con ese cargo antes de finalizar la nota.");
        }
        return directoras.get(0);
    }

    private void validarParaOficializar(Nota nota) {
        if (nota.getFechaOficial() == null) throw new IllegalStateException("La nota no tiene una fecha oficial válida.");
        if (nota.getNumero() == null || !NotaNumeroService.OCUPADO.equals(nota.getNumero().getEstado())) {
            throw new IllegalStateException("El número de la nota no está ocupado correctamente.");
        }
        if (nota.getAsunto() == null || nota.getAsunto().isBlank()) throw new IllegalStateException("El asunto es obligatorio para oficializar.");
        if (nota.getDestinatarioNombre() == null || nota.getDestinatarioNombre().isBlank()) throw new IllegalStateException("El nombre del destinatario es obligatorio para oficializar.");
        if (nota.getDestinatarioCargo() == null || nota.getDestinatarioCargo().isBlank()) throw new IllegalStateException("El cargo del destinatario es obligatorio para oficializar.");
        if (nota.getInstitucion() == null || !Boolean.TRUE.equals(nota.getInstitucion().getActivo())) throw new IllegalStateException("Debes seleccionar una institución activa para oficializar.");
        if (nota.getDepartamento() != null && (!nota.getDepartamento().isActivo()
                || !java.util.Objects.equals(nota.getInstitucion().getId(), nota.getDepartamento().getOrganization().getId()))) {
            throw new IllegalStateException("Debes seleccionar un departamento activo de la institución para oficializar.");
        }
        if (nota.getContenido() == null || nota.getContenido().isBlank()) throw new IllegalStateException("El contenido de la nota es obligatorio para oficializar.");
    }

    private String nombreCompleto(Usuario usuario) {
        return String.join(" ", usuario.getNombre() == null ? "" : usuario.getNombre(),
            usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(),
            usuario.getApellido() == null ? "" : usuario.getApellido()).trim().replaceAll("\\s+", " ");
    }

    private String limpiar(String valor) { return valor == null ? "" : valor.trim().replaceAll("\\s+", " "); }
    private String limpiarDescripcion(String valor) { return valor == null ? "" : valor.trim(); }
}
