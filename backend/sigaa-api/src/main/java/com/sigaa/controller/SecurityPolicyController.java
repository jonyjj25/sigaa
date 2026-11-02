package com.sigaa.controller;

import com.sigaa.model.*;
import com.sigaa.repository.*;
import com.sigaa.service.SecurityPolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/security")
public class SecurityPolicyController {

    private static final String CORS_DEPLOYMENT_ONLY = "CORS se configura únicamente en el backend por ambiente";
    private static final String DEPLOYMENT_ONLY = "Este parámetro técnico se configura únicamente en el backend";

    private final SecurityPolicyRepository policyRepository;
    private final IpRestrictionRepository ipRestrictionRepository;
    private final AllowedOriginRepository allowedOriginRepository;
    private final AllowedHttpMethodRepository allowedHttpMethodRepository;
    private final AllowedHttpHeaderRepository allowedHttpHeaderRepository;
    private final SecurityPolicyService securityPolicyService;

    public SecurityPolicyController(SecurityPolicyRepository policyRepository,
                                     IpRestrictionRepository ipRestrictionRepository,
                                     AllowedOriginRepository allowedOriginRepository,
                                     AllowedHttpMethodRepository allowedHttpMethodRepository,
                                     AllowedHttpHeaderRepository allowedHttpHeaderRepository,
                                     SecurityPolicyService securityPolicyService) {
        this.policyRepository = policyRepository;
        this.ipRestrictionRepository = ipRestrictionRepository;
        this.allowedOriginRepository = allowedOriginRepository;
        this.allowedHttpMethodRepository = allowedHttpMethodRepository;
        this.allowedHttpHeaderRepository = allowedHttpHeaderRepository;
        this.securityPolicyService = securityPolicyService;
    }

    // ========== Policies CRUD ==========

    @GetMapping("/policies")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<PolicyResponse>> getPolicies() {
        return ResponseEntity.ok(policyRepository.findAllByOrderByCategoriaAscNombreAsc().stream().map(this::response).toList());
    }

    @PutMapping("/policies/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<PolicyResponse> updatePolicy(@PathVariable Long id, @Valid @RequestBody UpdatePolicyRequest request) {
        return securityPolicyService.updatePolicy(id, request.getValor()).map(saved -> {
            securityPolicyService.refreshCache();
            return ResponseEntity.ok(response(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/policies/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<PolicyResponse> getPolicyById(@PathVariable Long id) {
        return policyRepository.findById(id)
                .map(policy -> ResponseEntity.ok(response(policy)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/policies/categoria/{categoria}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<PolicyResponse>> getPoliciesByCategoria(@PathVariable String categoria) {
        return ResponseEntity.ok(policyRepository.findByCategoria(categoria).stream().map(this::response).toList());
    }

    // ========== Restricciones IP históricas: solo consulta ==========

    @GetMapping("/ip-restrictions")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<IpRestriction>> getIpRestrictions(@RequestParam(required = false) String tipo) {
        if (tipo != null) {
            return ResponseEntity.ok(ipRestrictionRepository.findByTipoOrderByDireccionIpAsc(tipo));
        }
        return ResponseEntity.ok(ipRestrictionRepository.findAll());
    }

    @PostMapping("/ip-restrictions")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> createIpRestriction(@RequestBody IpRestriction request) {
        throw new AccessDeniedException(DEPLOYMENT_ONLY);
    }

    @PutMapping("/ip-restrictions/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<IpRestriction> updateIpRestriction(@PathVariable Long id, @RequestBody IpRestriction request) {
        throw new AccessDeniedException(DEPLOYMENT_ONLY);
    }

    @DeleteMapping("/ip-restrictions/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> deleteIpRestriction(@PathVariable Long id) {
        throw new AccessDeniedException(DEPLOYMENT_ONLY);
    }

    @PatchMapping("/ip-restrictions/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<IpRestriction> toggleIpRestriction(@PathVariable Long id) {
        throw new AccessDeniedException(DEPLOYMENT_ONLY);
    }

    // ========== CORS histórico: consulta solamente, sin efecto en el despliegue ==========

    @GetMapping("/allowed-origins")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<AllowedOrigin>> getAllowedOrigins() {
        return ResponseEntity.ok(allowedOriginRepository.findAll());
    }

    @PostMapping("/allowed-origins")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> createAllowedOrigin(@RequestBody AllowedOrigin request) {
        throw new AccessDeniedException(CORS_DEPLOYMENT_ONLY);
    }

    @PutMapping("/allowed-origins/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<AllowedOrigin> updateAllowedOrigin(@PathVariable Long id, @RequestBody AllowedOrigin request) {
        throw new AccessDeniedException(CORS_DEPLOYMENT_ONLY);
    }

    @DeleteMapping("/allowed-origins/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> deleteAllowedOrigin(@PathVariable Long id) {
        throw new AccessDeniedException(CORS_DEPLOYMENT_ONLY);
    }

    // ========== Allowed Methods ==========

    @GetMapping("/allowed-methods")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<AllowedHttpMethod>> getAllowedMethods() {
        return ResponseEntity.ok(allowedHttpMethodRepository.findAll());
    }

    // ========== Allowed Headers ==========

    @GetMapping("/allowed-headers")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<List<AllowedHttpHeader>> getAllowedHeaders() {
        return ResponseEntity.ok(allowedHttpHeaderRepository.findAll());
    }

    // ========== Utility ==========

    @PostMapping("/policies/refresh-cache")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<?> refreshCache() {
        securityPolicyService.refreshCache();
        return ResponseEntity.ok(Map.of("message", "Cache actualizado"));
    }

    public static class UpdatePolicyRequest {
        @NotBlank(message = "valor es obligatorio y no puede estar vacío")
        @Size(max = 255, message = "valor no puede superar 255 caracteres")
        private String valor;
        public String getValor() { return valor; }
        public void setValor(String valor) { this.valor = valor; }
    }

    public record PolicyResponse(Long id, String nombre, String valor, String descripcion, String categoria,
                                 long lockVersion, LocalDateTime createdAt, LocalDateTime updatedAt,
                                 boolean administrable, Long maximoServidorMb) {}

    private PolicyResponse response(SecurityPolicy policy) {
        boolean functionalFile = securityPolicyService.isAdministrable(policy)
                && securityPolicyService.isFunctionalFileLimit(policy);
        return new PolicyResponse(policy.getId(), policy.getNombre(),
                functionalFile ? String.valueOf(securityPolicyService.getMaxFileSizeMB()) : policy.getValor(),
                policy.getDescripcion(), policy.getCategoria(), policy.getLockVersion(), policy.getCreatedAt(), policy.getUpdatedAt(),
                securityPolicyService.isAdministrable(policy),
                functionalFile ? securityPolicyService.getServerMaxFileSizeMB() : null);
    }

}
