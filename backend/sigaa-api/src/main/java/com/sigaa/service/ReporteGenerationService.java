package com.sigaa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigaa.dto.ReporteGeneracionRequest;
import com.sigaa.model.Acta;
import com.sigaa.model.AsistenciaRegistro;
import com.sigaa.model.Reporte;
import com.sigaa.model.Reunion;
import com.sigaa.model.Tarea;
import com.sigaa.model.Usuario;
import com.sigaa.repository.ActaRepository;
import com.sigaa.repository.AsistenciaRegistroRepository;
import com.sigaa.repository.ReporteRepository;
import com.sigaa.repository.ReunionRepository;
import com.sigaa.repository.TareaRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Construye y conserva instantáneas de datos operativos para los informes del sistema. */
@Service
public class ReporteGenerationService {
    private final ReporteRepository reportes;
    private final ReunionRepository reuniones;
    private final ActaRepository actas;
    private final AsistenciaRegistroRepository asistencia;
    private final TareaRepository tareas;
    private final ReunionService reunionAccess;
    private final ActaAccessService actaAccess;
    private final TareaAccessService tareaAccess;
    private final AsistenciaAccessService asistenciaAccess;
    private final com.sigaa.repository.UsuarioRepository usuarios;
    /** La instantánea solo contiene texto, listas y mapas; no requiere configuración de Spring. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReporteGenerationService(ReporteRepository reportes, ReunionRepository reuniones,
                                    ActaRepository actas, AsistenciaRegistroRepository asistencia,
                                    TareaRepository tareas, ReunionService reunionAccess, ActaAccessService actaAccess,
                                    TareaAccessService tareaAccess, AsistenciaAccessService asistenciaAccess,
                                    com.sigaa.repository.UsuarioRepository usuarios) {
        this.reportes = reportes;
        this.reuniones = reuniones;
        this.actas = actas;
        this.asistencia = asistencia;
        this.tareas = tareas;
        this.reunionAccess = reunionAccess;
        this.actaAccess = actaAccess;
        this.tareaAccess = tareaAccess;
        this.asistenciaAccess = asistenciaAccess;
        this.usuarios = usuarios;
    }

    public List<Map<String, String>> plantillas() {
        return List.of(
                plantilla("Reuniones", "Resumen de reuniones", "Agenda, estados y modalidades del período."),
                plantilla("Actas", "Actas institucionales", "Actas por estado, responsable y fecha."),
                plantilla("Asistencia", "Registro de asistencia", "Entradas registradas y estado de presencia."),
                plantilla("Tareas", "Seguimiento de tareas", "Pendientes, progreso, prioridad y fechas límite."));
    }

    public boolean soporta(String modulo) {
        return List.of("Reuniones", "Actas", "Asistencia", "Tareas").contains(modulo);
    }

    @Transactional
    public Reporte generar(ReporteGeneracionRequest request, String generadoPor) {
        String nombre = nombrePlantilla(request.plantilla());
        return generar(nombre, request.plantilla(), "ejecutivo", request.formato(),
                request.fechaDesde(), request.fechaHasta(), generadoPor);
    }

    @Transactional
    public Reporte generar(String nombre, String modulo, String tipo, String formato,
                            LocalDate desde, LocalDate hasta, String generadoPor) {
        if (!soporta(modulo)) throw new IllegalArgumentException("La plantilla de reporte no está disponible");
        if (subdirectorActual()) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String permisoFuente = "Asistencia".equals(modulo) ? "Configuracion QR:Ver" : modulo + ":Ver";
            if (auth.getAuthorities().stream().noneMatch(a -> permisoFuente.equals(a.getAuthority()))) {
                throw new org.springframework.security.access.AccessDeniedException("Sin permiso de consulta del módulo de origen");
            }
        }
        Snapshot snapshot = construirInstantanea(modulo, desde, hasta);
        Reporte reporte = new Reporte();
        reporte.setNombre(nombre == null || nombre.isBlank() ? nombrePlantilla(modulo) : nombre.trim());
        reporte.setModulo(modulo);
        reporte.setTipo(tipo == null || tipo.isBlank() ? "ejecutivo" : tipo);
        reporte.setFormato("PDF".equals(formato) ? "PDF" : "CSV");
        reporte.setFecha(LocalDate.now());
        reporte.setGeneradoPor(generadoPor == null || generadoPor.isBlank() ? "Sistema" : generadoPor);
        reporte.setCreadoPorUsuarioId(usuarioActualId());
        reporte.setDescripcion(descripcion(snapshot));
        reporte.setContenido(aJson(snapshot));
        return reportes.save(reporte);
    }

    @Transactional(readOnly = true)
    public Exportacion exportar(Reporte reporte, String formatoSolicitado) {
        Snapshot snapshot = leerInstantanea(reporte);
        boolean pdf = "PDF".equalsIgnoreCase(formatoSolicitado);
        return pdf
                ? new Exportacion(aPdf(snapshot), "application/pdf", nombreArchivo(reporte.getNombre(), "pdf"))
                : new Exportacion(aCsv(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "text/csv;charset=UTF-8", nombreArchivo(reporte.getNombre(), "csv"));
    }

    public Long usuarioActualId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() ? null : usuarios.findIdByCorreo(auth.getName()).orElse(null);
    }

    private boolean subdirectorActual() {
        return InstitutionalRoles.scopedSubdirector(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    private Snapshot construirInstantanea(String modulo, LocalDate desde, LocalDate hasta) {
        return switch (modulo) {
            case "Reuniones" -> resumenReuniones(desde, hasta);
            case "Actas" -> resumenActas(desde, hasta);
            case "Asistencia" -> resumenAsistencia(desde, hasta);
            case "Tareas" -> resumenTareas(desde, hasta);
            default -> throw new IllegalArgumentException("Plantilla no disponible");
        };
    }

    private Snapshot resumenReuniones(LocalDate desde, LocalDate hasta) {
        List<Reunion> datos = (subdirectorActual() ? reunionAccess.reunionesVisiblesActual()
            : reuniones.findAllOrderByFechaDescHoraDesc()).stream()
                .filter(r -> enRango(r.getFecha(), desde, hasta)).toList();
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("Total de reuniones", String.valueOf(datos.size()));
        resumen.put("Programadas", String.valueOf(datos.stream().filter(r -> "programada".equalsIgnoreCase(r.getEstado())).count()));
        resumen.put("Realizadas", String.valueOf(datos.stream().filter(r -> "realizada".equalsIgnoreCase(r.getEstado())).count()));
        List<List<String>> filas = datos.stream().map(r -> List.of(valor(r.getCodigo()), valor(r.getFecha()),
                valor(r.getTitulo()), valor(r.getEstado()), valor(r.getModalidad()), valor(r.getOrganizador()))).toList();
        return new Snapshot("Resumen de reuniones", periodo(desde, hasta), resumen,
                List.of("Código", "Fecha", "Reunión", "Estado", "Modalidad", "Organizador"), filas);
    }

    private Snapshot resumenActas(LocalDate desde, LocalDate hasta) {
        List<Acta> datos = (subdirectorActual() ? actaAccess.visiblesActual() : actas.findAllByOrderByFechaDesc())
            .stream().filter(a -> enRango(a.getFecha(), desde, hasta)).toList();
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("Total de actas", String.valueOf(datos.size()));
        resumen.put("Terminadas", String.valueOf(datos.stream().filter(a -> "Terminada".equalsIgnoreCase(a.getEstado())).count()));
        resumen.put("Borradores", String.valueOf(datos.stream().filter(a -> "Borrador".equalsIgnoreCase(a.getEstado())).count()));
        List<List<String>> filas = datos.stream().map(a -> List.of(valor(a.getCodigo()), valor(a.getFecha()),
                valor(a.getTitulo()), valor(a.getTipo()), valor(a.getEstado()), valor(a.getResponsable()))).toList();
        return new Snapshot("Actas institucionales", periodo(desde, hasta), resumen,
                List.of("Código", "Fecha", "Acta", "Tipo", "Estado", "Responsable"), filas);
    }

    private Snapshot resumenAsistencia(LocalDate desde, LocalDate hasta) {
        List<AsistenciaRegistro> datos = asistencia.findAll().stream()
                .filter(a -> a.getSesion() != null && enRango(a.getFecha(), desde, hasta))
                .filter(a -> !subdirectorActual() || asistenciaAccess.puedeAcceder(a.getSesion()))
                .toList();
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("Registros de asistencia", String.valueOf(datos.size()));
        resumen.put("Presentes", String.valueOf(datos.stream().filter(a -> "presente".equalsIgnoreCase(a.getEstado())).count()));
        resumen.put("Sesiones con registros", String.valueOf(datos.stream().map(a -> a.getSesion() == null ? null : a.getSesion().getId()).filter(java.util.Objects::nonNull).distinct().count()));
        List<List<String>> filas = datos.stream().map(a -> List.of(valor(a.getFecha()), nombreUsuario(a.getUsuario()),
                valor(a.getSesion().getNombre()), valor(a.getHoraEntrada()), valor(a.getEstado()))).toList();
        return new Snapshot("Registro de asistencia", periodo(desde, hasta), resumen,
                List.of("Fecha", "Usuario", "Sesión", "Entrada", "Estado"), filas);
    }

    private Snapshot resumenTareas(LocalDate desde, LocalDate hasta) {
        List<Tarea> datos = (subdirectorActual() ? tareaAccess.tareasVisiblesActual() : tareas.findAllByOrderByFechaCreacionDesc()).stream()
                .filter(t -> enRango(t.getFechaCreacion(), desde, hasta)).toList();
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("Total de tareas", String.valueOf(datos.size()));
        resumen.put("Completadas", String.valueOf(datos.stream().filter(t -> "completada".equalsIgnoreCase(t.getEstado())).count()));
        resumen.put("Pendientes", String.valueOf(datos.stream().filter(t -> !"completada".equalsIgnoreCase(t.getEstado())).count()));
        List<List<String>> filas = datos.stream().map(t -> List.of(valor(t.getCodigo()), valor(t.getFechaCreacion()),
                valor(t.getTitulo()), valor(t.getEstado()), valor(t.getPrioridad()), valor(t.getProgreso()) + "%")).toList();
        return new Snapshot("Seguimiento de tareas", periodo(desde, hasta), resumen,
                List.of("Código", "Creación", "Tarea", "Estado", "Prioridad", "Progreso"), filas);
    }

    private Snapshot leerInstantanea(Reporte reporte) {
        if (reporte.getContenido() != null && !reporte.getContenido().isBlank()) {
            try { return objectMapper.readValue(reporte.getContenido(), Snapshot.class); }
            catch (JsonProcessingException ignored) { }
        }
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("Generado por", valor(reporte.getGeneradoPor()));
        resumen.put("Descripción", valor(reporte.getDescripcion()));
        return new Snapshot(valor(reporte.getNombre()), "Fecha de generación: " + valor(reporte.getFecha()), resumen,
                List.of("Módulo", "Tipo", "Formato"), List.of(List.of(valor(reporte.getModulo()), valor(reporte.getTipo()), valor(reporte.getFormato()))));
    }

    private String aCsv(Snapshot snapshot) {
        List<String> lineas = new ArrayList<>();
        lineas.add(esc(snapshot.titulo()));
        lineas.add(esc(snapshot.periodo()));
        snapshot.resumen().forEach((clave, valor) -> lineas.add(esc(clave) + "," + esc(valor)));
        lineas.add("");
        lineas.add(String.join(",", snapshot.columnas().stream().map(this::esc).toList()));
        snapshot.filas().forEach(fila -> lineas.add(String.join(",", fila.stream().map(this::esc).toList())));
        return String.join("\r\n", lineas);
    }

    private byte[] aPdf(Snapshot snapshot) {
        List<String> lineas = new ArrayList<>();
        lineas.add(snapshot.titulo());
        lineas.add(snapshot.periodo());
        lineas.add("");
        snapshot.resumen().forEach((clave, valor) -> lineas.add(clave + ": " + valor));
        lineas.add("");
        lineas.add(String.join(" | ", snapshot.columnas()));
        snapshot.filas().forEach(fila -> lineas.add(String.join(" | ", fila)));
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDType1Font fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPageContentStream contenido = null;
            float y = 0;
            for (String linea : lineas) {
                if (contenido == null || y < 48) {
                    if (contenido != null) { contenido.endText(); contenido.close(); }
                    documento.addPage(new PDPage(PDRectangle.LETTER));
                    contenido = new PDPageContentStream(documento, documento.getPage(documento.getNumberOfPages() - 1));
                    contenido.beginText(); contenido.setFont(fuente, 9); contenido.setLeading(13); contenido.newLineAtOffset(38, 756); y = 756;
                }
                contenido.showText(normalizarPdf(linea, 135)); contenido.newLine(); y -= 13;
            }
            if (contenido != null) { contenido.endText(); contenido.close(); }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el PDF del reporte", e);
        }
    }

    private Map<String, String> plantilla(String modulo, String nombre, String descripcion) {
        return Map.of("modulo", modulo, "nombre", nombre, "descripcion", descripcion);
    }

    private String aJson(Snapshot snapshot) {
        try { return objectMapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo guardar la instantánea", e); }
    }

    private String descripcion(Snapshot snapshot) {
        return snapshot.periodo() + ". " + snapshot.resumen().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue()).reduce((a, b) -> a + " · " + b).orElse("");
    }

    private String nombrePlantilla(String modulo) {
        return switch (modulo) {
            case "Reuniones" -> "Resumen de reuniones";
            case "Actas" -> "Actas institucionales";
            case "Asistencia" -> "Registro de asistencia";
            case "Tareas" -> "Seguimiento de tareas";
            default -> "Informe SIGAA";
        };
    }

    private String periodo(LocalDate desde, LocalDate hasta) { return "Período: " + desde + " a " + hasta; }
    private boolean enRango(LocalDate fecha, LocalDate desde, LocalDate hasta) { return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta); }
    private String valor(Object valor) { return valor == null ? "" : String.valueOf(valor); }
    private String nombreUsuario(Usuario usuario) {
        if (usuario == null) return "";
        return String.join(" ", valor(usuario.getNombre()), valor(usuario.getSegundoNombre()), valor(usuario.getApellido())).trim();
    }
    private String esc(String valor) { return '"' + valor.replace("\"", "\"\"") + '"'; }
    private String nombreArchivo(String nombre, String extension) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("^-|-$", "");
        return (base.isBlank() ? "informe-sigaa" : base.toLowerCase(Locale.ROOT)) + "." + extension;
    }
    private String normalizarPdf(String texto, int limite) {
        String limpio = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
        return limpio.length() <= limite ? limpio : limpio.substring(0, limite - 3) + "...";
    }

    public record Snapshot(String titulo, String periodo, Map<String, String> resumen,
                           List<String> columnas, List<List<String>> filas) { }
    public record Exportacion(byte[] contenido, String mediaType, String archivo) { }
}
