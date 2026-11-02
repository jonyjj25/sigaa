package com.sigaa.controller;

import com.sigaa.model.AuditLog;
import com.sigaa.dto.PageResponse;
import com.sigaa.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.persistence.criteria.Predicate;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogRepository repository;
    public AuditLogController(AuditLogRepository repository) { this.repository = repository; }
    @GetMapping
    @PreAuthorize("hasRole('administrador')")
    public List<AuditLog> listar() { return repository.findTop200ByOrderByFechaDesc(); }

    @GetMapping("/page")
    @PreAuthorize("hasRole('administrador')")
    public PageResponse<AuditLog> listarPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        String search = optional(q);
        String action = optional(accion);
        String entity = optional(entidad);
        LocalDateTime from = desde == null ? null : desde.atStartOfDay();
        LocalDateTime to = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
        Specification<AuditLog> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null) {
                String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("usuario")), like),
                        builder.like(builder.lower(root.get("accion")), like),
                        builder.like(builder.lower(root.get("entidad")), like),
                        builder.like(builder.lower(root.get("detalle")), like),
                        builder.like(builder.lower(root.get("direccionIp")), like)));
            }
            if (action != null) predicates.add(builder.equal(root.get("accion"), action));
            if (entity != null) predicates.add(builder.equal(root.get("entidad"), entity));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("fecha"), from));
            if (to != null) predicates.add(builder.lessThan(root.get("fecha"), to));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(filters,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "fecha"))));
    }

    @GetMapping("/facets")
    @PreAuthorize("hasRole('administrador')")
    public Map<String, List<String>> facets() {
        return Map.of("acciones", repository.findDistinctAcciones(),
                "entidades", repository.findDistinctEntidades());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('administrador')")
    public Map<String, Long> summary() {
        return Map.of(
                "total", repository.count(),
                "hoy", repository.countByFechaGreaterThanEqual(LocalDate.now().atStartOfDay()),
                "criticos", repository.countEventosCriticos(),
                "usuarios", repository.countDistinctUsuarios());
    }
    @GetMapping("/filter")
    @PreAuthorize("hasRole('administrador')")
    public List<AuditLog> filtrar(@RequestParam String entidad, @RequestParam Long entidadId) {
        return repository.findByEntidadAndEntidadIdOrderByFechaDesc(entidad, entidadId);
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
