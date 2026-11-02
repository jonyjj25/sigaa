package com.sigaa.controller;

import com.sigaa.model.AuditLog;
import com.sigaa.model.Tarea;
import com.sigaa.model.Reunion;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.BackupRepository;
import com.sigaa.repository.InventarioActivoRepository;
import com.sigaa.repository.ReunionRepository;
import com.sigaa.repository.RoleRepository;
import com.sigaa.repository.TareaRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.ReunionService;
import com.sigaa.service.TareaAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final BackupRepository backupRepository;
    private final AuditLogRepository auditLogRepository;
    private final TareaRepository tareaRepository;
    private final InventarioActivoRepository inventarioRepository;
    private final ReunionRepository reunionRepository;
    private final ReunionService reunionService;
    private final TareaAccessService tareaAccessService;

    public DashboardController(UsuarioRepository usuarioRepository, RoleRepository roleRepository,
                               BackupRepository backupRepository, AuditLogRepository auditLogRepository,
                               TareaRepository tareaRepository, InventarioActivoRepository inventarioRepository,
                               ReunionRepository reunionRepository, ReunionService reunionService,
                               TareaAccessService tareaAccessService) {
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.backupRepository = backupRepository;
        this.auditLogRepository = auditLogRepository;
        this.tareaRepository = tareaRepository;
        this.inventarioRepository = inventarioRepository;
        this.reunionRepository = reunionRepository;
        this.reunionService = reunionService;
        this.tareaAccessService = tareaAccessService;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long usuarios = usuarioRepository.count();
        long roles = roleRepository.count();
        long bloqueados = usuarioRepository.countByBloqueadoTrue();
        String ultimoRespaldo = backupRepository.findAllByOrderByFechaDesc().stream()
            .findFirst().map(b -> b.getFecha().toString()).orElse("N/A");
        Map<String, Object> m = new HashMap<>();
        m.put("usuarios", usuarios);
        m.put("roles", roles);
        m.put("ultimoRespaldo", ultimoRespaldo);
        m.put("alertas", bloqueados);
        m.put("estado", "Operativo");
        return m;
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('administrador')")
    public List<Map<String, Object>> kpis() {
        LocalDateTime now = LocalDateTime.now();
        var users = usuarioRepository.resumirDashboard(now.minusDays(14), now.minusDays(7), now);
        return List.of(
            kpi("Usuarios", users.getTotal(), "Total registrados", "people-outline", users.getUsuariosActual(), users.getUsuariosAnterior(), "#8b5cf6"),
            kpi("Activos", users.getActivos(), "Cuentas activas", "checkmark-circle-outline", users.getActivosActual(), users.getActivosAnterior(), "#10b981"),
            kpi("Bloqueados", users.getBloqueados(), "Cuentas bloqueadas", "lock-closed-outline", users.getBloqueadosActual(), users.getBloqueadosAnterior(), "#f59e0b"),
            mapOf("label", "Roles", "value", roleRepository.count(), "subtitle", "Roles del sistema", "icon", "shield-outline", "trend", "neutral", "trendValue", "Configurados", "trendDescription", "Roles disponibles actualmente", "color", "#3b82f6")
        );
    }

    @GetMapping("/actividades")
    @PreAuthorize("hasRole('administrador')")
    public List<Map<String, Object>> actividades() {
        return auditLogRepository.findTop10ByOrderByFechaDesc().stream().map(log -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", String.valueOf(log.getId()));
            m.put("user", log.getUsuario());
            m.put("action", friendlyAction(log.getAccion()));
            m.put("target", friendlyTarget(log));
            m.put("timestamp", log.getFecha().toString());
            m.put("avatar", "");
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/chart-data")
    @PreAuthorize("hasRole('administrador')")
    public Map<String, Object> chartData() {
        Map<Integer, Long> byMonth = auditLogRepository.countByMonthSince(LocalDateTime.now().minusMonths(6)).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).intValue(), row -> ((Number) row[1]).longValue()));
        String[] meses = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};
        int currentMonth = LocalDateTime.now().getMonthValue();
        List<String> categories = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int idx = ((currentMonth - 6 + i) % 12 + 12) % 12;
            categories.add(meses[idx]);
            data.add(byMonth.getOrDefault(idx + 1, 0L));
        }
        Map<String, Object> series = new HashMap<>();
        series.put("name", "Actividades");
        series.put("data", data);
        Map<String, Object> result = new HashMap<>();
        result.put("categories", categories);
        result.put("series", List.of(series));
        return result;
    }

    @GetMapping("/tareas-por-estado")
    public Map<String, Object> tareasPorEstado() {
        boolean admin = isAdmin();
        List<Tarea> all = admin ? List.of() : tareasPersonales();
        Map<String, Long> counts = admin ? groupedCounts(tareaRepository.countByEstadoAgrupado()) : Map.of();
        long porHacer = admin ? counts.getOrDefault("por_hacer", 0L) : all.stream().filter(t -> "por_hacer".equals(t.getEstado())).count();
        long enProgreso = admin ? counts.getOrDefault("en_progreso", 0L) : all.stream().filter(t -> "en_progreso".equals(t.getEstado())).count();
        long completada = admin ? counts.getOrDefault("completada", 0L) : all.stream().filter(t -> "completada".equals(t.getEstado())).count();
        Map<String, Object> result = new HashMap<>();
        result.put("items", List.of(
            mapOf("label", "Por hacer", "value", porHacer, "color", "#f59e0b"),
            mapOf("label", "En progreso", "value", enProgreso, "color", "#3b82f6"),
            mapOf("label", "Completadas", "value", completada, "color", "#10b981")
        ));
        return result;
    }

    @GetMapping("/calendario")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> calendario() {
        List<Map<String, Object>> eventos = new ArrayList<>();
        tareasPersonales().stream()
            .filter(t -> t.getFechaLimite() != null)
            .forEach(t -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", "tarea-" + t.getId());
                m.put("title", t.getTitulo());
                if (t.getHoraLimite() != null) {
                    m.put("date", t.getFechaLimite().toString() + "T" + t.getHoraLimite().toString());
                    m.put("allDay", false);
                } else {
                    m.put("date", t.getFechaLimite().toString());
                    m.put("allDay", true);
                }
                m.put("type", "tarea");
                m.put("description", t.getDescripcion());
                m.put("estado", t.getEstado()); m.put("prioridad", t.getPrioridad()); m.put("responsable", t.getAsignado());
                m.put("modulo", "TAREAS"); m.put("registroId", t.getId()); eventos.add(m);
            });
        reunionesPersonales().stream().filter(r -> r.getFecha() != null).forEach(r -> {
            Map<String, Object> m = new HashMap<>(); m.put("id", "reunion-" + r.getId()); m.put("title", r.getTitulo());
            m.put("date", r.getHoraInicio() == null ? r.getFecha().toString() : r.getFecha() + "T" + r.getHoraInicio());
            m.put("endDate", r.getHoraFin() == null ? null : r.getFecha() + "T" + r.getHoraFin()); m.put("allDay", r.getHoraInicio() == null);
            m.put("type", "reunion"); m.put("description", r.getDescripcion()); m.put("estado", r.getEstado());
            m.put("lugar", r.getLugar()); m.put("modalidad", r.getModalidad()); m.put("organizador", r.getOrganizador());
            m.put("modulo", "REUNIONES"); m.put("registroId", r.getId()); eventos.add(m);
        });
        return eventos;
    }

    @GetMapping("/inventario")
    @PreAuthorize("hasRole('administrador')")
    public List<Map<String, Object>> inventario() {
        Map<String, Long> grupos = inventarioRepository.countByEstadoAgrupado().stream()
                .collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> ((Number) row[1]).longValue()));
        List<Map<String, Object>> items = new ArrayList<>();
        String[] colores = {"#2563eb", "#22c55e", "#f59e0b", "#ef4444", "#a855f7", "#64748b"};
        int idx = 0;
        for (Map.Entry<String, Long> e : grupos.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("nombre", e.getKey());
            m.put("cantidad", e.getValue());
            m.put("color", colores[idx % colores.length]);
            idx++;
            items.add(m);
        }
        return items;
    }

    @GetMapping("/reuniones-por-estado")
    public Map<String, Object> reunionesPorEstado() {
        boolean admin = isAdmin();
        List<Reunion> all = admin ? List.of() : reunionesPersonales();
        Map<String, Long> counts = admin ? groupedCounts(reunionRepository.countByEstadoAgrupado()) : Map.of();
        long programadas = admin ? counts.getOrDefault("programada", 0L) : all.stream().filter(r -> "programada".equals(r.getEstado())).count();
        long enCurso = admin ? counts.getOrDefault("en_curso", 0L) : all.stream().filter(r -> "en_curso".equals(r.getEstado())).count();
        long realizadas = admin ? counts.getOrDefault("realizada", 0L) : all.stream().filter(r -> "realizada".equals(r.getEstado())).count();
        long canceladas = admin ? counts.getOrDefault("cancelada", 0L) : all.stream().filter(r -> "cancelada".equals(r.getEstado())).count();
        Map<String, Object> result = new HashMap<>();
        result.put("items", List.of(
            mapOf("label", "Programadas", "value", programadas, "color", "#3b82f6"),
            mapOf("label", "En curso", "value", enCurso, "color", "#f59e0b"),
            mapOf("label", "Realizadas", "value", realizadas, "color", "#10b981"),
            mapOf("label", "Canceladas", "value", canceladas, "color", "#ef4444")
        ));
        return result;
    }

    @GetMapping("/kpis-usuario")
    public List<Map<String, Object>> kpisUsuario() {
        List<Tarea> tareas = tareasPersonales(); List<Reunion> reuniones = reunionesPersonales();
        long totalTareas = tareas.size();
        long totalReuniones = reuniones.size();
        long reunionesHoy = reuniones.stream().filter(r -> LocalDate.now().equals(r.getFecha()) && "programada".equals(r.getEstado())).count();

        List<Map<String, Object>> kpis = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        kpis.add(kpi("Tareas", totalTareas, "Total asignadas", "checkbox-outline", contarCreadas(tareas.stream().map(Tarea::getCreatedAt).toList(), now, 7), contarCreadas(tareas.stream().map(Tarea::getCreatedAt).toList(), now.minusDays(7), 7), "#f59e0b"));
        kpis.add(kpi("Reuniones", totalReuniones, "Total programadas", "people-outline", contarCreadas(reuniones.stream().map(Reunion::getCreatedAt).toList(), now, 7), contarCreadas(reuniones.stream().map(Reunion::getCreatedAt).toList(), now.minusDays(7), 7), "#3b82f6"));
        long ayer = reuniones.stream().filter(r -> LocalDate.now().minusDays(1).equals(r.getFecha()) && "programada".equals(r.getEstado())).count();
        kpis.add(kpi("Hoy", reunionesHoy, "Reuniones programadas", "calendar-outline", reunionesHoy, ayer, "#10b981", "hoy", "ayer"));
        List<Tarea> completas = tareas.stream().filter(t -> "completada".equals(t.getEstado())).toList();
        kpis.add(kpi("Completadas", completas.size(), "Tareas finalizadas", "checkmark-circle-outline", contarCreadas(completas.stream().map(Tarea::getUpdatedAt).toList(), now, 7), contarCreadas(completas.stream().map(Tarea::getUpdatedAt).toList(), now.minusDays(7), 7), "#8b5cf6"));
        return kpis;
    }

    private List<Tarea> tareasPersonales() { return tareaAccessService.tareasVisiblesActual(); }
    private static Map<String, Long> groupedCounts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> ((Number) row[1]).longValue()));
    }
    private List<Reunion> reunionesPersonales() { return reunionService.reunionesVisiblesActual(); }
    private boolean isAdmin() { return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a -> "ROLE_administrador".equalsIgnoreCase(a.getAuthority())); }

    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            m.put((String) entries[i], entries[i + 1]);
        }
        return m;
    }

    private static boolean enPeriodo(LocalDateTime date, LocalDateTime start, LocalDateTime end) { return date != null && !date.isBefore(start) && date.isBefore(end); }
    private static long contarCreadas(List<LocalDateTime> dates, LocalDateTime end, int days) { return dates.stream().filter(date -> enPeriodo(date, end.minusDays(days), end)).count(); }
    private static Map<String, Object> kpi(String label, long value, String subtitle, String icon, long current, long previous, String color) {
        return kpi(label, value, subtitle, icon, current, previous, color, "esta semana", "semana anterior");
    }
    private static Map<String, Object> kpi(String label, long value, String subtitle, String icon, long current, long previous, String color, String currentPeriod, String previousPeriod) {
        String trend = current > previous ? "up" : current < previous ? "down" : "neutral";
        String trendValue = current + " " + currentPeriod;
        String trendDescription = current + " " + currentPeriod + " · " + previous + " " + previousPeriod;
        return mapOf("label", label, "value", value, "subtitle", subtitle, "icon", icon, "trend", trend, "trendValue", trendValue, "trendDescription", trendDescription, "color", color);
    }

    private static String friendlyAction(String action) {
        if (action == null || action.isBlank()) return "realizó una acción en";
        String normalized = action.toUpperCase();
        if (normalized.contains("CREAR")) return "creó";
        if (normalized.contains("ELIMINAR")) return "eliminó";
        if (normalized.contains("APROBAR")) return "aprobó";
        if (normalized.contains("RECHAZAR")) return "rechazó";
        if (normalized.contains("CAMBIAR")) return "cambió";
        if (normalized.contains("ACTUALIZAR") || normalized.contains("EDITAR")) return "actualizó";
        return action.toLowerCase().replace('_', ' ');
    }

    private static String friendlyTarget(AuditLog log) {
        String entity = log.getEntidad() == null ? "un registro" : log.getEntidad();
        if ("UserPreferences".equalsIgnoreCase(entity)) return "sus preferencias personales";
        String detail = log.getDetalle() == null ? "" : log.getDetalle();
        int beforeIndex = detail.indexOf(" Antes:");
        if (beforeIndex >= 0) detail = detail.substring(0, beforeIndex);
        detail = detail.replaceAll("(?i)por usuario \\d+", "").trim();
        if (detail.length() > 110) detail = detail.substring(0, 107) + "...";
        return detail.isBlank() ? entity.toLowerCase() : entity.toLowerCase() + ": " + detail;
    }
}
