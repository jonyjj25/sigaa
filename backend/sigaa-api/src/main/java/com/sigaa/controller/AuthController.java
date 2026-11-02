package com.sigaa.controller;

import com.sigaa.util.LogDiagnostics;
import com.sigaa.dto.LoginRequest;
import com.sigaa.dto.LoginResponse;
import com.sigaa.dto.PasswordChangeRequest;
import com.sigaa.dto.PasswordResetCodeRequest;
import com.sigaa.dto.PasswordResetConfirmRequest;
import com.sigaa.dto.PasswordResetEmailRequest;
import com.sigaa.dto.ProfileUpdateRequest;
import com.sigaa.dto.UserInfoResponse;
import com.sigaa.dto.UsuarioResponse;
import com.sigaa.dto.MembresiaResponse;
import com.sigaa.model.AuditLog;
import com.sigaa.model.Role;
import com.sigaa.model.Usuario;
import com.sigaa.model.Usuario2fa;
import com.sigaa.repository.AuditLogRepository;
import com.sigaa.repository.Usuario2faRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import com.sigaa.repository.VwUsuarioRolRepository;
import com.sigaa.security.JwtUtil;
import com.sigaa.service.SecurityPolicyService;
import com.sigaa.service.SecurityIpService;
import com.sigaa.service.LoginRateLimitService;
import com.sigaa.service.SecurityPasswordService;
import com.sigaa.service.PasswordHistoryService;
import com.sigaa.service.PasswordResetService;
import com.sigaa.service.TwoFactorService;
import com.sigaa.util.UploadStorage;
import com.sigaa.util.FileContentValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final Path FOTO_DIR = Paths.get("uploads/fotografias");
    private static final Set<String> ALLOWED_PHOTO_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024;
    private static final String ACCESS_COOKIE = "sigaa_access";

    @Value("${app.auth.cookie-secure:false}")
    private boolean secureCookie;

    @Value("${app.auth.cookie-same-site:Strict}")
    private String cookieSameSite;

    private final UsuarioRepository usuarioRepository;
    private final UsuarioUnidadRepository usuarioUnidadRepository;
    private final VwUsuarioRolRepository vwUsuarioRolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final Usuario2faRepository usuario2faRepository;
    private final TwoFactorService twoFactorService;
    private final SecurityPolicyService securityPolicyService;
    private final SecurityIpService securityIpService;
    private final LoginRateLimitService loginRateLimitService;
    private final SecurityPasswordService securityPasswordService;
    private final PasswordHistoryService passwordHistoryService;
    private final PasswordResetService passwordResetService;
    private final AuditLogRepository auditLogRepository;

    public AuthController(UsuarioRepository usuarioRepository,
                          UsuarioUnidadRepository usuarioUnidadRepository,
                          VwUsuarioRolRepository vwUsuarioRolRepository,
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                          Usuario2faRepository usuario2faRepository,
                          TwoFactorService twoFactorService,
                          SecurityPolicyService securityPolicyService,
                          SecurityIpService securityIpService,
                          LoginRateLimitService loginRateLimitService,
                          SecurityPasswordService securityPasswordService,
                          PasswordHistoryService passwordHistoryService,
                          PasswordResetService passwordResetService,
                          AuditLogRepository auditLogRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioUnidadRepository = usuarioUnidadRepository;
        this.vwUsuarioRolRepository = vwUsuarioRolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.usuario2faRepository = usuario2faRepository;
        this.twoFactorService = twoFactorService;
        this.securityPolicyService = securityPolicyService;
        this.securityIpService = securityIpService;
        this.loginRateLimitService = loginRateLimitService;
        this.securityPasswordService = securityPasswordService;
        this.passwordHistoryService = passwordHistoryService;
        this.passwordResetService = passwordResetService;
        this.auditLogRepository = auditLogRepository;
        try { Files.createDirectories(FOTO_DIR); } catch (IOException ignored) {}
    }

    private Set<String> loadRoles(Long usuarioId) {
        return vwUsuarioRolRepository.findRoleNamesByUsuarioId(usuarioId)
            .stream()
            .filter(name -> !com.sigaa.service.InstitutionalRoles.retired(name)).collect(Collectors.toSet());
    }

    private Set<String> loadPermissions(Usuario usuario) {
        Set<String> permisos = new HashSet<>();
        if (usuario.getRoles() != null) {
            usuario.getRoles().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActivo()))
                .filter(r -> !com.sigaa.service.InstitutionalRoles.retired(r.getNombre()))
                .flatMap(r -> r.getPermisos().stream())
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .forEach(p -> permisos.add(p.getModulo() + ":" + p.getAccion()));
        }
        usuarioUnidadRepository.findByUsuarioId(usuario.getId())
            .stream()
            .map(m -> m.getRol())
            .filter(java.util.Objects::nonNull)
            .filter(r -> Boolean.TRUE.equals(r.getActivo()))
            .filter(r -> !com.sigaa.service.InstitutionalRoles.retired(r.getNombre()))
            .flatMap(r -> r.getPermisos().stream())
            .filter(p -> Boolean.TRUE.equals(p.getActivo()))
            .forEach(p -> permisos.add(p.getModulo() + ":" + p.getAccion()));
        return permisos;
    }

    private boolean usuarioTiene2fa(Long usuarioId) {
        return usuario2faRepository.findByUsuarioId(usuarioId)
            .map(Usuario2fa::getEnabled)
            .orElse(false);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        var access = securityIpService.checkAccess(ipAddress);
        if (!access.allowed()) {
            LoginResponse error = new LoginResponse();
            error.setStatus("error");
            error.setMessage("Acceso restringido por la política de seguridad.");
            return ResponseEntity.status(403).body(error);
        }
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        var rateDecision = loginRateLimitService.checkLogin(ipAddress, username);
        if (!rateDecision.allowed()) {
            return rateLimitedResponse(rateDecision.retryAfterSeconds());
        }
        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsernameIgnoreCase(username);
        String loginFailureMessage = "Nombre de usuario o contraseña incorrectos";

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();

            // Check if account is locked
            if (Boolean.TRUE.equals(usuario.getBloqueado())) {
                // Auto-unlock if lockout duration has expired
                if (usuario.getBloqueadoHasta() != null && LocalDateTime.now().isAfter(usuario.getBloqueadoHasta())) {
                    usuario.setBloqueado(false);
                    usuario.setBloqueadoHasta(null);
                    usuario.setIntentosFallidos(0);
                    usuarioRepository.save(usuario);
                    log.info("Bloqueo de cuenta vencido; cuenta habilitada automáticamente");
                } else {
                    logSecurityAudit(usuario.getCorreo(), "INICIO_SESION_BLOQUEADO", usuario.getId(),
                            "Intento de acceso a una cuenta bloqueada", ipAddress);
                    LoginResponse error = new LoginResponse();
                    error.setStatus("error");
                    error.setMessage(buildLockoutMessage(usuario.getBloqueadoHasta()));
                    return ResponseEntity.status(401).body(error);
                }
            }


            if (Boolean.TRUE.equals(usuario.getActivo()) && passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
                loginRateLimitService.recordLoginSuccess(ipAddress, username);
                if (usuario.isTemporaryPasswordExpired()) return expiredTemporaryPasswordResponse();
                if (usuario.isPasswordExpired()) return expiredPasswordResponse();
                // SUCCESS: reset failed attempts, update last access
                usuario.setIntentosFallidos(0);
                usuario.setBloqueado(false);
                usuario.setUltimoAcceso(LocalDateTime.now());
                usuarioRepository.save(usuario);

                boolean requiredChange = Boolean.TRUE.equals(usuario.getCambioContrasenaRequerido());
                Set<String> allRoles = requiredChange ? Set.of() : loadRoles(usuario.getId());
                String rolesStr = String.join(",", allRoles);
                Set<String> permisos = requiredChange ? Set.of() : loadPermissions(usuario);

                if (securityPolicyService.isTwoFactorEnabled() && usuarioTiene2fa(usuario.getId())) {
                    long twoFaExpiryMs = securityPolicyService.getTwoFactorTokenExpiryMs();
                    String tempToken = jwtUtil.generateToken(
                        usuario.getCorreo(), rolesStr,
                        Map.of("2fa_pending", "true", "token_version", usuario.getTokenVersion()), twoFaExpiryMs);
                    twoFactorService.generateCode(usuario.getCorreo());

                    LoginResponse response = new LoginResponse();
                    response.setStatus("success");
                    response.setRequires2fa(true);
                    response.setTempToken(tempToken);
                    response.setCorreo(usuario.getCorreo());
                    response.setNombre(usuario.getNombre());
                    logSecurityAudit(usuario.getCorreo(), "SEGUNDO_FACTOR_SOLICITADO", usuario.getId(),
                            "Se solicitó verificación de segundo factor", ipAddress);
                    return ResponseEntity.ok(response);
                }

                String token = jwtUtil.generateToken(usuario.getCorreo(), rolesStr, usuario.getTokenVersion());
                LoginResponse response = new LoginResponse();
                response.setStatus("success");
                response.setToken(token);
                response.setId(usuario.getId());
                response.setCorreo(usuario.getCorreo());
                response.setNombre(usuario.getNombre());
                response.setRoles(rolesStr);
                response.setPermisos(permisos);
                response.setFotografiaRuta(requiredChange ? null : usuario.getFotografiaRuta());
                response.setCambioContrasenaRequerido(Boolean.TRUE.equals(usuario.getCambioContrasenaRequerido()));
                logSecurityAudit(usuario.getCorreo(), "INICIAR_SESION", usuario.getId(),
                        "Inicio de sesión completado", ipAddress);
                return authenticatedResponse(response, token);
            } else {
                // FAILED: increment failed attempts
                int currentAttempts = usuario.getIntentosFallidos() != null ? usuario.getIntentosFallidos() : 0;
                int maxAttempts = securityPolicyService.getMaxLoginAttempts();
                int newAttempts = currentAttempts + 1;
                int remainingAttempts = Math.max(0, maxAttempts - newAttempts);
                usuario.setIntentosFallidos(newAttempts);

                if (newAttempts >= maxAttempts) {
                    long lockoutMs = securityPolicyService.getLockoutDurationMs();
                    if (!Boolean.TRUE.equals(usuario.getBloqueado())) usuario.revokeSessions();
                    usuario.setBloqueado(true);
                    usuario.setBloqueadoHasta(LocalDateTime.now().plusSeconds(lockoutMs / 1000));
                    log.warn("Cuenta bloqueada después de {} intentos fallidos", newAttempts);
                }

                if (remainingAttempts > 1) {
                    loginFailureMessage = "Nombre de usuario o contraseña incorrectos. Le quedan " + remainingAttempts
                            + " intentos antes de que su cuenta sea bloqueada.";
                }

                usuarioRepository.save(usuario);
                log.warn("Inicio de sesión rechazado; intento {} de {}", newAttempts, maxAttempts);
            }
        }

        loginRateLimitService.recordLoginFailure(ipAddress, username);
        logSecurityAudit(usuarioOpt.map(Usuario::getCorreo).orElse(username), "INICIO_SESION_FALLIDO",
                usuarioOpt.map(Usuario::getId).orElse(null), "Credenciales rechazadas", ipAddress);
        LoginResponse errorResponse = new LoginResponse();
        errorResponse.setStatus("error");
        errorResponse.setMessage(loginFailureMessage);
        return ResponseEntity.status(401).body(errorResponse);
    }

    private String buildLockoutMessage(LocalDateTime bloqueadoHasta) {
        long configuredMinutes = Math.max(1, (securityPolicyService.getLockoutDurationMs() + 59_999) / 60_000);
        if (bloqueadoHasta == null) {
            return "Su cuenta fue bloqueada según la política de acceso. Intente iniciar sesión nuevamente cuando el bloqueo sea levantado.";
        }

        long remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), bloqueadoHasta).getSeconds());
        long remainingMinutes = Math.max(1, (remainingSeconds + 59) / 60);
        return "Su cuenta fue bloqueada temporalmente por " + configuredMinutes + " " + minuteLabel(configuredMinutes)
                + ". Podrá volver a iniciar sesión en aproximadamente " + remainingMinutes + " " + minuteLabel(remainingMinutes) + ".";
    }

    private String minuteLabel(long minutes) {
        return minutes == 1 ? "minuto" : "minutos";
    }

    @PostMapping("/2fa/authenticate")
    public ResponseEntity<LoginResponse> authenticate2fa(@RequestBody Authenticate2faRequest request, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        if (!securityIpService.checkAccess(ipAddress).allowed()) {
            LoginResponse error = new LoginResponse();
            error.setStatus("error");
            error.setMessage("Acceso restringido por la politica de seguridad.");
            return ResponseEntity.status(403).body(error);
        }
        if (!jwtUtil.hasClaim(request.getTempToken(), "2fa_pending")) {
            LoginResponse error = new LoginResponse();
            error.setStatus("error");
            error.setMessage("Token inválido o expirado");
            return ResponseEntity.status(401).body(error);
        }

        String email = jwtUtil.extractUsername(request.getTempToken());
        var rateDecision = loginRateLimitService.checkSecondFactor(ipAddress, email);
        if (!rateDecision.allowed()) {
            return rateLimitedResponse(rateDecision.retryAfterSeconds());
        }

        if (!twoFactorService.verifyCode(email, request.getCode())) {
            loginRateLimitService.recordSecondFactorFailure(ipAddress, email);
            logSecurityAudit(email, "SEGUNDO_FACTOR_FALLIDO", null,
                    "Código de segundo factor rechazado", ipAddress);
            LoginResponse error = new LoginResponse();
            error.setStatus("error");
            error.setMessage("Código inválido o expirado");
            return ResponseEntity.status(401).body(error);
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(email);
        if (usuarioOpt.isEmpty()) {
            LoginResponse error = new LoginResponse();
            error.setStatus("error");
            error.setMessage("Usuario no encontrado");
            return ResponseEntity.status(404).body(error);
        }

        Usuario usuario = usuarioOpt.get();
        if (!Boolean.TRUE.equals(usuario.getActivo()) || Boolean.TRUE.equals(usuario.getBloqueado())) {
            return ResponseEntity.status(401).body(new LoginResponse("error", "La cuenta no está disponible"));
        }
        if (usuario.isTemporaryPasswordExpired()) return expiredTemporaryPasswordResponse();
        if (usuario.isPasswordExpired()) return expiredPasswordResponse();
        boolean requiredChange = Boolean.TRUE.equals(usuario.getCambioContrasenaRequerido());
        Set<String> allRoles = requiredChange ? Set.of() : loadRoles(usuario.getId());
        String rolesStr = String.join(",", allRoles);
        Set<String> permisos = requiredChange ? Set.of() : loadPermissions(usuario);
        if (jwtUtil.extractLongClaim(request.getTempToken(), "token_version") != usuario.getTokenVersion()) {
            return ResponseEntity.status(401).body(new LoginResponse("error", "La sesión de verificación ya no es válida"));
        }
        String token = jwtUtil.generateToken(usuario.getCorreo(), rolesStr, usuario.getTokenVersion());

        LoginResponse response = new LoginResponse();
        response.setStatus("success");
        response.setToken(token);
        response.setId(usuario.getId());
        response.setCorreo(usuario.getCorreo());
        response.setNombre(usuario.getNombre());
        response.setRoles(rolesStr);
        response.setPermisos(permisos);
        response.setCambioContrasenaRequerido(Boolean.TRUE.equals(usuario.getCambioContrasenaRequerido()));
        loginRateLimitService.recordSecondFactorSuccess(email);
        logSecurityAudit(usuario.getCorreo(), "INICIAR_SESION_2FA", usuario.getId(),
                "Inicio de sesión completado con segundo factor", ipAddress);
        return authenticatedResponse(response, token);
    }

    private ResponseEntity<LoginResponse> rateLimitedResponse(long retryAfterSeconds) {
        LoginResponse error = new LoginResponse();
        error.setStatus("error");
        error.setMessage("Demasiados intentos. Intente nuevamente en unos minutos.");
        return ResponseEntity.status(429)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfterSeconds)))
                .body(error);
    }

    private ResponseEntity<LoginResponse> expiredTemporaryPasswordResponse() {
        LoginResponse error = new LoginResponse("error", "La contraseña temporal expiró. Solicita un nuevo restablecimiento.");
        error.setCode("TEMPORARY_PASSWORD_EXPIRED");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    private ResponseEntity<LoginResponse> expiredPasswordResponse() {
        LoginResponse error = new LoginResponse("error", "La contraseña venció. Usa Recuperar contraseña para establecer una nueva.");
        error.setCode("PASSWORD_EXPIRED");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(Authentication auth, HttpServletRequest httpRequest) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            // Política explícita: cerrar sesión revoca TODAS las sesiones de la cuenta.
            usuarioRepository.revokeSessionsByCorreo(auth.getName());
            logSecurityAudit(auth.getName(), "CERRAR_SESION", null, "Cierre de sesión solicitado", httpRequest.getRemoteAddr());
        }
        ResponseCookie expiredCookie = ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    private ResponseEntity<LoginResponse> authenticatedResponse(LoginResponse response, String token) {
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofMillis(securityPolicyService.getJwtExpirationMs()))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody PasswordResetEmailRequest request, HttpServletRequest httpRequest) {
        try {
            String message = passwordResetService.requestReset(request.getCorreo(), httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of("message", message));
        } catch (PasswordResetService.ResetRateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Espera un momento antes de solicitar otro código."));
        } catch (PasswordResetService.ResetMailUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "La recuperación de contraseña no está disponible temporalmente."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingresa un correo electrónico válido."));
        }
    }

    @PostMapping("/resend-reset-code")
    public ResponseEntity<Map<String, String>> resendResetCode(@RequestBody PasswordResetEmailRequest request, HttpServletRequest httpRequest) {
        return forgotPassword(request, httpRequest);
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyResetCode(@RequestBody PasswordResetCodeRequest request, HttpServletRequest httpRequest) {
        var result = passwordResetService.verifyCode(request.getCorreo(), request.getCode(), httpRequest.getRemoteAddr());
        if (result.status() == PasswordResetService.Status.SUCCESS) return ResponseEntity.ok(Map.of("resetToken", result.resetToken()));
        String error = switch (result.status()) {
            case EXPIRED -> "El código expiró. Solicita uno nuevo.";
            case BLOCKED -> "El código fue bloqueado por demasiados intentos. Solicita uno nuevo.";
            default -> "El código no es válido.";
        };
        return ResponseEntity.status(401).body(Map.of("error", error));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody PasswordResetConfirmRequest request, HttpServletRequest httpRequest) {
        var result = passwordResetService.resetPassword(request.getResetToken(), request.getNewPassword(), httpRequest.getRemoteAddr());
        if (result.status() == PasswordResetService.Status.SUCCESS) return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
        return ResponseEntity.status(result.status() == PasswordResetService.Status.WEAK_PASSWORD ? 400 : 401)
                .body(Map.of("error", result.message()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        Usuario usuario = usuarioOpt.get();
        if (Boolean.TRUE.equals(usuario.getBloqueado())) {
            return ResponseEntity.status(403).build();
        }
        if (Boolean.TRUE.equals(usuario.getCambioContrasenaRequerido())) {
            return ResponseEntity.ok(Map.of("nombre", usuario.getNombre(), "correo", usuario.getCorreo(),
                    "cambioContrasenaRequerido", true));
        }
        Set<String> allRoles = loadRoles(usuario.getId());
        String rolesStr = String.join(",", allRoles);
        Set<String> permisos = loadPermissions(usuario);
        boolean has2fa = usuarioTiene2fa(usuario.getId());

        UserInfoResponse response = new UserInfoResponse(usuario.getNombre(), usuario.getCorreo(), rolesStr, permisos);
        response.setId(usuario.getId());
        response.setHas2fa(has2fa);
        response.setCambioContrasenaRequerido(false);
        response.setFotografiaRuta(usuario.getFotografiaRuta());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/profile")
    public ResponseEntity<UsuarioResponse> getMyProfile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(toResponse(usuarioOpt.get()));
    }

    @PutMapping("/me/profile")
    @Transactional
    public ResponseEntity<?> updateMyProfile(Authentication authentication,
                                             @RequestBody ProfileUpdateRequest request) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        Usuario usuario = usuarioOpt.get();
        if (request.getNombre() != null) usuario.setNombre(request.getNombre().trim());
        if (request.getSegundoNombre() != null) usuario.setSegundoNombre(request.getSegundoNombre().trim());
        if (request.getApellido() != null) usuario.setApellido(request.getApellido().trim());
        if (request.getTelefono() != null) usuario.setTelefono(request.getTelefono());
        if (request.getCelular() != null) usuario.setCelular(request.getCelular());
        if (request.getCorreoAlterno() != null) usuario.setCorreoAlterno(request.getCorreoAlterno());
        if (request.getDocumentoIdentidad() != null) usuario.setDocumentoIdentidad(request.getDocumentoIdentidad().trim());
        if (request.getTipoDocumento() != null) usuario.setTipoDocumento(request.getTipoDocumento().trim());
        if (request.getCargo() != null) usuario.setCargo(request.getCargo().trim());
        if (request.getInstitucion() != null) usuario.setInstitucion(request.getInstitucion().trim());
        if (request.getDepartamento() != null) usuario.setDepartamento(request.getDepartamento().trim());
        if (request.getDireccion() != null) usuario.setDireccion(request.getDireccion());
        if (request.getUnidadAdministrativa() != null) usuario.setUnidadAdministrativa(request.getUnidadAdministrativa().trim());
        if (request.getCodigoInterno() != null) usuario.setCodigoInterno(request.getCodigoInterno().trim());
        if (request.getObservaciones() != null) usuario.setObservaciones(request.getObservaciones().trim());

        Usuario saved = usuarioRepository.save(usuario);
        logAudit("ACTUALIZAR_PERFIL", "Usuario", saved.getId(),
                "Perfil actualizado por el usuario: " + saved.getCorreo());
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/me/password-policy")
    public ResponseEntity<?> getMyPasswordPolicy(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of(
                "minLength", securityPolicyService.getPasswordMinLength(),
                "maxLength", securityPolicyService.getPasswordMaxLength(),
                "requiresUppercase", securityPolicyService.requiresUppercase(),
                "requiresLowercase", securityPolicyService.requiresLowercase(),
                "requiresDigits", securityPolicyService.requiresDigits(),
                "requiresSpecialChars", securityPolicyService.requiresSpecialChars(),
                "historySize", securityPolicyService.getPasswordHistorySize()
        ));
    }

    @PatchMapping("/me/password")
    @Transactional
    public ResponseEntity<?> changeMyPassword(Authentication authentication,
                                              @RequestBody PasswordChangeRequest request) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        Usuario usuario = usuarioOpt.get();

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contrasena actual es requerida"));
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), usuario.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contrasena actual es incorrecta"));
        }
        var passwordValidation = securityPasswordService.validate(request.getNewPassword());
        if (!passwordValidation.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", String.join(". ", passwordValidation.errors())));
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()
                || request.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contrasena debe tener al menos 6 caracteres"));
        }

        try {
            passwordHistoryService.changePassword(usuario, request.getNewPassword());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
        usuarioRepository.save(usuario);
        logAudit("CAMBIAR_CONTRASENA_PROPIO", "Usuario", usuario.getId(),
                "Contrasena cambiada por el usuario: " + usuario.getCorreo());
        return ResponseEntity.ok(Map.of("message", "Contrasena actualizada correctamente"));
    }

    @PatchMapping("/me/required-password")
    @Transactional
    public ResponseEntity<?> completeRequiredPassword(Authentication authentication, @RequestBody PasswordChangeRequest request) {
        if (authentication == null || authentication.getName() == null) return ResponseEntity.status(401).build();
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(authentication.getName()).orElse(null);
        if (usuario == null) return ResponseEntity.status(404).build();
        if (!Boolean.TRUE.equals(usuario.getCambioContrasenaRequerido())) {
            return ResponseEntity.badRequest().body(Map.of("error", "No hay un cambio obligatorio pendiente"));
        }
        if (usuario.isTemporaryPasswordExpired()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "TEMPORARY_PASSWORD_EXPIRED",
                    "error", "La contraseña temporal expiró. Solicita un nuevo restablecimiento."));
        }
        var validation = securityPasswordService.validate(request.getNewPassword());
        if (!validation.valid()) return ResponseEntity.badRequest().body(Map.of("error", String.join(". ", validation.errors())));
        try { passwordHistoryService.changePassword(usuario, request.getNewPassword()); }
        catch (IllegalArgumentException ex) {
            // Un fallo posterior a la modificación de la entidad tampoco debe
            // confirmar cambios por haber convertido la excepción en HTTP 400.
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        usuarioRepository.save(usuario);
        logAudit("COMPLETAR_CAMBIO_OBLIGATORIO", "Usuario", usuario.getId(), "Contraseña definitiva establecida");
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada. Inicia sesión nuevamente"));
    }

    @PostMapping("/me/photo")
    @Transactional
    public ResponseEntity<?> uploadMyPhoto(Authentication authentication,
                                           @RequestParam("file") MultipartFile file) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo esta vacio"));
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo excede el limite de 5MB"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) originalName = "photo";
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) ext = originalName.substring(dot + 1).toLowerCase();
        if (!ALLOWED_PHOTO_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Formato no permitido. Use JPG, JPEG, PNG o WEBP"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo debe ser una imagen"));
        }

        try {
            FileContentValidator.validate(file, ALLOWED_PHOTO_EXTENSIONS);
        } catch (IOException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "El contenido de la imagen no es válido"));
        }

        Usuario usuario = usuarioOpt.get();

        if (usuario.getFotografiaRuta() != null && !usuario.getFotografiaRuta().isBlank()) {
            try {
                String oldPath = usuario.getFotografiaRuta();
                if (oldPath.startsWith("/uploads/")) {
                    Files.deleteIfExists(Paths.get(oldPath.substring(1)));
                }
            } catch (IOException ignored) {}
        }

        try {
            Path targetPath = UploadStorage.store(FOTO_DIR, file);
            String photoUrl = "/uploads/fotografias/" + targetPath.getFileName();
            usuario.setFotografiaRuta(photoUrl);
            usuarioRepository.save(usuario);
            logAudit("ACTUALIZAR_FOTOGRAFIA", "Usuario", usuario.getId(),
                    "Fotografia actualizada por el usuario: " + usuario.getCorreo());
            return ResponseEntity.ok(Map.of("fotografiaRuta", photoUrl));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar el archivo"));
        }
    }

    @DeleteMapping("/me/photo")
    @Transactional
    public ResponseEntity<?> removeMyPhoto(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return ResponseEntity.status(401).build();
        Usuario usuario = usuarioRepository.findByCorreo(authentication.getName()).orElse(null);
        if (usuario == null) return ResponseEntity.status(404).build();
        String oldPath = usuario.getFotografiaRuta();
        if (oldPath != null && oldPath.startsWith("/uploads/")) {
            try { Files.deleteIfExists(Paths.get(oldPath.substring(1))); } catch (IOException ignored) {}
        }
        usuario.setFotografiaRuta("");
        usuarioRepository.save(usuario);
        logAudit("ELIMINAR_FOTOGRAFIA", "Usuario", usuario.getId(), "Fotografia eliminada por el usuario: " + usuario.getCorreo());
        return ResponseEntity.ok(Map.of("fotografiaRuta", ""));
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        UsuarioResponse resp = new UsuarioResponse();
        resp.setId(usuario.getId());
        resp.setNombre(usuario.getNombre());
        resp.setSegundoNombre(usuario.getSegundoNombre());
        resp.setApellido(usuario.getApellido());
        resp.setCorreo(usuario.getCorreo());
        resp.setCorreoAlterno(usuario.getCorreoAlterno());
        resp.setActivo(usuario.getActivo());
        resp.setBloqueado(usuario.getBloqueado());
        resp.setCreadoEn(usuario.getCreadoEn());
        resp.setActualizadoEn(usuario.getActualizadoEn());
        resp.setUltimoAcceso(usuario.getUltimoAcceso());
        resp.setDocumentoIdentidad(usuario.getDocumentoIdentidad());
        resp.setTipoDocumento(usuario.getTipoDocumento());
        resp.setTelefono(usuario.getTelefono());
        resp.setCelular(usuario.getCelular());
        resp.setCargo(usuario.getCargo());
        resp.setDepartamento(usuario.getDepartamento());
        resp.setDireccion(usuario.getDireccion());
        resp.setUnidadAdministrativa(usuario.getUnidadAdministrativa());
        resp.setUsername(usuario.getUsername());
        resp.setFechaExpiracion(usuario.getFechaExpiracion());
        resp.setCambioContrasenaRequerido(usuario.getCambioContrasenaRequerido());
        resp.setFotografiaRuta(usuario.getFotografiaRuta());
        resp.setCodigoInterno(usuario.getCodigoInterno());
        resp.setObservaciones(usuario.getObservaciones());

        List<String> roleNames = usuario.getRoles().stream()
                .map(Role::getNombre)
                .collect(Collectors.toList());
        resp.setRoles(roleNames);

        List<MembresiaResponse> membresias = usuarioUnidadRepository.findByUsuarioId(usuario.getId())
                .stream()
                .map(m -> new MembresiaResponse(
                        m.getId(),
                        m.getUnidad() != null ? m.getUnidad().getId() : null,
                        m.getUnidad() != null ? m.getUnidad().getNombre() : null,
                        m.getRol().getId(),
                        m.getRol().getNombre()
                ))
                .collect(Collectors.toList());
        resp.setMembresias(membresias);

        return resp;
    }

    private void logAudit(String accion, String entidad, Long entidadId, String detalle) {
        try {
            AuditLog log = new AuditLog();
            log.setUsuario(authentication() != null ? authentication().getName() : "sistema");
            log.setAccion(accion);
            log.setEntidad(entidad);
            log.setEntidadId(entidadId);
            log.setDetalle(detalle);
            log.setFecha(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception ignored) {}
    }

    private void logSecurityAudit(String usuario, String accion, Long entidadId, String detalle, String direccionIp) {
        try {
            AuditLog log = new AuditLog();
            log.setUsuario(usuario == null || usuario.isBlank() ? "desconocido" : usuario);
            log.setAccion(accion);
            log.setEntidad("Seguridad");
            log.setEntidadId(entidadId);
            log.setDetalle(detalle);
            log.setDireccionIp(direccionIp);
            log.setFecha(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception e) {
            log.warn("No se pudo registrar el evento de seguridad {}: {}", accion, LogDiagnostics.describe(e));
        }
    }

    private Authentication authentication() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        } catch (Exception e) {
            return null;
        }
    }

    public static class Authenticate2faRequest {
        private String tempToken;
        private String code;
        public String getTempToken() { return tempToken; }
        public void setTempToken(String tempToken) { this.tempToken = tempToken; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }
}
