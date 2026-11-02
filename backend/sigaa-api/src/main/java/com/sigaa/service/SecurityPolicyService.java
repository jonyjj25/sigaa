package com.sigaa.service;

import com.sigaa.config.SecurityDeploymentProperties;
import com.sigaa.model.SecurityPolicy;
import com.sigaa.repository.SecurityPolicyRepository;
import com.sigaa.util.FileContentValidator;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@EnableConfigurationProperties(SecurityDeploymentProperties.class)
public class SecurityPolicyService {
    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyService.class);

    private final SecurityPolicyRepository policyRepository;
    private static final long MB = 1024L * 1024L;
    private static final Set<String> BOOLEAN_POLICIES = Set.of(
            "requiere mayusculas", "requiere minusculas", "requiere numeros", "requiere caracteres especiales",
            "cambio obligatorio de contrasena", "requiere cambio de contrasena", "bloqueo ascendente",
            "autenticacion de dos factores", "restriccion por horario");
    private static final Set<String> FUNCTIONAL_POLICIES = Set.of(
            "longitud minima de contrasena", "longitud maxima de contrasena", "historial de contrasenas",
            "requiere mayusculas", "requiere minusculas", "requiere numeros", "requiere caracteres especiales",
            "expiracion de contrasena (dias)", "cambio obligatorio de contrasena", "requiere cambio de contrasena",
            "intentos fallidos maximos", "tiempo de bloqueo (min)", "politica de bloqueo", "bloqueo ascendente",
            "autenticacion de dos factores", "restriccion por horario", "horario inicio acceso", "horario fin acceso",
            "tamano maximo de archivos (mb)", "tipos de archivos permitidos");

    private final SecurityDeploymentProperties deployment;
    private final MultipartConfigElement multipart;
    private final long jwtExpirationMs;

    private volatile Map<String, String> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor();

    public SecurityPolicyService(SecurityPolicyRepository policyRepository,
                                  SecurityDeploymentProperties deployment, MultipartConfigElement multipart,
                                  @Value("${jwt.expiration-ms:3600000}") long jwtExpirationMs) {
        this.policyRepository = policyRepository;
        this.deployment = deployment;
        this.multipart = multipart;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    @PostConstruct
    public void init() {
        // JWT se expresa con precisión de segundos y una sesión no debe durar más de un día.
        if (jwtExpirationMs < 1000 || jwtExpirationMs > TimeUnit.DAYS.toMillis(1)) {
            throw new IllegalArgumentException("jwt.expiration-ms debe estar entre 1000 y 86400000 ms");
        }
        if (deployment.twoFactorTokenExpiryMs() > jwtExpirationMs) {
            throw new IllegalArgumentException("app.security.two-factor-token-expiry-ms no puede superar jwt.expiration-ms");
        }
        if (multipart.getMaxFileSize() <= 0 || multipart.getMaxRequestSize() <= 0) {
            throw new IllegalArgumentException("spring.servlet.multipart.max-file-size y max-request-size deben ser positivos y finitos");
        }
        if (multipart.getMaxFileSize() > multipart.getMaxRequestSize()) {
            throw new IllegalArgumentException("spring.servlet.multipart.max-file-size no puede superar spring.servlet.multipart.max-request-size");
        }
        // La BD no configura la red. Esta comprobación evita desactivar una restricción
        // histórica al desplegar sin haber decidido explícitamente su reemplazo técnico.
        if (deployment.ipRestrictionEnabled() == null && policyRepository.findAll().stream().anyMatch(policy ->
                "restriccion por ip".equals(policyKey(policy.getNombre())) && Boolean.parseBoolean(policy.getValor()))) {
            throw new IllegalStateException("Defina app.security.ip-restriction-enabled al migrar restricciones IP históricas");
        }
        refreshCache();
        refresher.scheduleAtFixedRate(this::refreshCache, 5, 5, TimeUnit.MINUTES);
    }

    public void refreshCache() {
        Map<String, String> newCache = new ConcurrentHashMap<>();
        policyRepository.findAll().stream().filter(this::isAdministrable)
                .forEach(p -> newCache.put(p.getNombre(), p.getValor()));
        cache = newCache;
        log.debug("Caché de políticas de seguridad actualizado: {} políticas", newCache.size());
    }

    public String getValue(String nombre, String defaultValue) {
        return getValue(cache, nombre, defaultValue);
    }

    private String getValue(Map<String, String> current, String nombre, String defaultValue) {
        String canonical = current.get(nombre);
        if (canonical != null) return canonical;
        // Conserva prioridad de las claves que el backend ya consumía. Solo usa
        // variantes históricas cuando falta esa clave, sin otra caché ni escritura.
        String key = policyKey(nombre);
        return current.entrySet().stream().filter(entry -> policyKey(entry.getKey()).equals(key))
                .map(Map.Entry::getValue).findFirst().orElse(defaultValue);
    }

    public boolean getBoolean(String nombre, boolean defaultValue) {
        String val = getValue(nombre, null);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    public int getInt(String nombre, int defaultValue) {
        String val = getValue(nombre, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLongMs(String nombre, long defaultMs) {
        String val = getValue(nombre, null);
        if (val == null) return defaultMs;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultMs;
        }
    }

    // JWT
    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    // Password
    public int getPasswordMinLength() {
        return getInt("Longitud mínima de contraseña", 8);
    }

    public int getPasswordMaxLength() {
        return getInt("Longitud máxima de contraseña", 128);
    }

    public boolean requiresUppercase() {
        return getBoolean("Requiere mayúsculas", true);
    }

    public boolean requiresLowercase() {
        return getBoolean("Requiere minúsculas", true);
    }

    public boolean requiresDigits() {
        return getBoolean("Requiere números", true);
    }

    public boolean requiresSpecialChars() {
        return getBoolean("Requiere caracteres especiales", true);
    }

    public int getPasswordHistorySize() {
        return getInt("Historial de contraseñas", 5);
    }

    public int getPasswordExpirationDays() {
        return getInt("Expiración de contraseña (días)", 90);
    }

    public boolean isForceChangeOnFirstLogin() {
        return getBoolean("Cambio obligatorio de contraseña", true);
    }

    public boolean requiresPeriodicPasswordChange() {
        return getBoolean("Requiere cambio de contraseña", false);
    }

    // Access
    public int getMaxLoginAttempts() {
        return getInt("Intentos fallidos máximos", 5);
    }

    public long getLockoutDurationMs() {
        return getLongMs("Tiempo de bloqueo (min)", 30) * 60 * 1000;
    }

    public String getLockoutPolicy() {
        return getValue("Política de bloqueo", "TEMPORAL");
    }

    public boolean isEscalatingLockout() {
        return getBoolean("Bloqueo ascendente", false);
    }

    // IP
    public boolean isIpRestrictionEnabled() {
        return Boolean.TRUE.equals(deployment.ipRestrictionEnabled());
    }

    public boolean isScheduleRestrictionEnabled() {
        return getBoolean("Restricción por horario", false);
    }

    public LocalTime getAccessStartTime() {
        return LocalTime.parse(getValue("Horario inicio acceso", "06:00"));
    }

    public LocalTime getAccessEndTime() {
        return LocalTime.parse(getValue("Horario fin acceso", "22:00"));
    }

    // Upload
    public long getMaxFileSizeMB() {
        return Math.min(getServerMaxFileSizeMB(), Math.max(0, getLongMs("Tamaño máximo de archivos (MB)", 10)));
    }

    public long getServerMaxFileSizeMB() {
        return Math.min(multipart.getMaxFileSize(), multipart.getMaxRequestSize()) / MB;
    }

    public long getMaxFileSizeBytes() {
        return getMaxFileSizeMB() * MB;
    }

    public String getAllowedFileExtensions() {
        return getValue("Tipos de archivos permitidos", "pdf,doc,docx,xls,xlsx,ppt,pptx,jpg,jpeg,png,gif,webp,zip,rar,txt,csv");
    }

    public long getMaxRequestSizeBytes() {
        return multipart.getMaxRequestSize();
    }

    // Audit
    // 2FA
    public boolean isTwoFactorEnabled() {
        return getBoolean("Autenticación de dos factores", false);
    }

    public long getTwoFactorTokenExpiryMs() {
        return deployment.twoFactorTokenExpiryMs();
    }

    // IP Restrictions
    public boolean isIpAllowed(String clientIp) {
        return deployment.isIpAllowed(clientIp);
    }

    public boolean isWithinAccessSchedule() {
        if (!isScheduleRestrictionEnabled()) return true;
        LocalTime now = LocalTime.now();
        LocalTime start = getAccessStartTime();
        LocalTime end = getAccessEndTime();
        if (start.isBefore(end) || start.equals(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }

    public boolean isAdministrable(SecurityPolicy policy) {
        return !Set.of("cors", "jwt").contains(policy.getCategoria().trim().toLowerCase(Locale.ROOT))
                && FUNCTIONAL_POLICIES.contains(policyKey(policy.getNombre()));
    }

    public boolean isFunctionalFileLimit(SecurityPolicy policy) {
        return "tamano maximo de archivos (mb)".equals(policyKey(policy.getNombre()));
    }

    public void validateFunctionalFileLimit(SecurityPolicy policy, String value) {
        if (isFunctionalFileLimit(policy)) number(policy.getNombre(), value, 0, getServerMaxFileSizeMB());
    }

    @Transactional
    public Optional<SecurityPolicy> updatePolicy(Long id, String rawValue) {
        List<SecurityPolicy> policies = policyRepository.findAllForUpdate();
        Optional<SecurityPolicy> selected = policies.stream().filter(policy -> policy.getId().equals(id)).findFirst();
        if (selected.isEmpty()) return Optional.empty();
        SecurityPolicy policy = selected.get();
        if (!isAdministrable(policy)) {
            throw new AccessDeniedException("Este parámetro técnico se configura únicamente en el backend");
        }
        if (rawValue == null || rawValue.isBlank() || rawValue.length() > 255) {
            throw new IllegalArgumentException("valor es obligatorio y debe tener entre 1 y 255 caracteres");
        }
        String key = policyKey(policy.getNombre());
        String value = validateValue(policy, rawValue.trim());
        Map<String, String> proposed = new LinkedHashMap<>();
        policies.stream().filter(this::isAdministrable).forEach(p -> proposed.put(p.getNombre(), p.getValor()));
        // Valida también los alias históricos con las mismas reglas, sin reescribirlos.
        proposed.replaceAll((name, previous) -> policyKey(name).equals(key) ? value : previous);
        proposed.put(policy.getNombre(), value);
        validateRelations(proposed, key);
        // Ninguna entidad se altera hasta haber validado el conjunto relacionado.
        policy.setValor(value);
        return Optional.of(policyRepository.saveAndFlush(policy));
    }

    private String validateValue(SecurityPolicy policy, String value) {
        String key = policyKey(policy.getNombre());
        if (BOOLEAN_POLICIES.contains(key)) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if (!Set.of("true", "false").contains(normalized)) {
                throw new IllegalArgumentException(policy.getNombre() + ": debe ser true o false");
            }
            return normalized;
        }
        switch (key) {
            // BCrypt rechaza entradas de más de 72 bytes: un mínimo mayor sería imposible de cumplir.
            case "longitud minima de contrasena" -> number(policy.getNombre(), value, 1, 72);
            // Conserva el máximo predeterminado actual; no cambia la validación de contraseñas de usuarios.
            case "longitud maxima de contrasena" -> number(policy.getNombre(), value, 1, 128);
            case "historial de contrasenas" -> number(policy.getNombre(), value, 0, 100);
            // 30 coincide con el umbral IP base; un bloqueo temporal no se prolonga más de un día.
            case "intentos fallidos maximos" -> number(policy.getNombre(), value, 1, 30);
            case "tiempo de bloqueo (min)" -> number(policy.getNombre(), value, 1, TimeUnit.DAYS.toMinutes(1));
            case "expiracion de contrasena (dias)" -> number(policy.getNombre(), value, 0, 365);
            case "tamano maximo de archivos (mb)" -> validateFunctionalFileLimit(policy, value);
            case "horario inicio acceso", "horario fin acceso" -> time(policy.getNombre(), value);
            case "politica de bloqueo" -> {
                String normalized = value.toUpperCase(Locale.ROOT);
                if (!Set.of("TEMPORAL", "PERMANENTE", "ASCENDENTE").contains(normalized)) {
                    throw new IllegalArgumentException(policy.getNombre() + ": debe ser TEMPORAL, PERMANENTE o ASCENDENTE");
                }
                return normalized;
            }
            case "tipos de archivos permitidos" -> {
                List<String> extensions = Arrays.stream(value.toLowerCase(Locale.ROOT).split(",", -1)).map(String::trim).toList();
                if (extensions.stream().anyMatch(extension -> !FileContentValidator.SUPPORTED_EXTENSIONS.contains(extension))) {
                    throw new IllegalArgumentException(policy.getNombre() + ": use extensiones compatibles separadas por comas, sin puntos, rutas ni comodines");
                }
                return String.join(",", extensions.stream().distinct().toList());
            }
            default -> throw new AccessDeniedException("Esta política no admite cambios");
        }
        return value;
    }

    private void validateRelations(Map<String, String> values, String key) {
        if (key.startsWith("longitud ") || Set.of("requiere mayusculas", "requiere minusculas",
                "requiere numeros", "requiere caracteres especiales").contains(key)) {
            long min = number("Longitud mínima de contraseña", getValue(values, "Longitud mínima de contraseña", "8"), 1, 72);
            long max = number("Longitud máxima de contraseña", getValue(values, "Longitud máxima de contraseña", "128"), 1, 128);
            if (min > max) throw new IllegalArgumentException("Longitud mínima de contraseña no puede superar Longitud máxima de contraseña");
            long requiredClasses = List.of("Requiere mayúsculas", "Requiere minúsculas", "Requiere números", "Requiere caracteres especiales")
                    .stream().filter(name -> Boolean.parseBoolean(getValue(values, name, "true"))).count();
            if (max < requiredClasses) {
                throw new IllegalArgumentException("Longitud máxima de contraseña no permite cumplir todos los requisitos de caracteres activos");
            }
        }
        if (Set.of("expiracion de contrasena (dias)", "requiere cambio de contrasena").contains(key)
                && Boolean.parseBoolean(getValue(values, "Requiere cambio de contraseña", "false"))
                && number("Expiración de contraseña (días)", getValue(values, "Expiración de contraseña (días)", "90"), 0, 365) == 0) {
            throw new IllegalArgumentException("Expiración de contraseña (días) debe ser mayor que cero si Requiere cambio de contraseña está activo");
        }
        if (Set.of("restriccion por horario", "horario inicio acceso", "horario fin acceso").contains(key)
                && Boolean.parseBoolean(getValue(values, "Restricción por horario", "false"))) {
            LocalTime start = time("Horario inicio acceso", getValue(values, "Horario inicio acceso", "06:00"));
            LocalTime end = time("Horario fin acceso", getValue(values, "Horario fin acceso", "22:00"));
            // El consumidor admite jornadas nocturnas: inicio > fin cruza medianoche.
            if (start.equals(end)) throw new IllegalArgumentException("Horario inicio acceso y Horario fin acceso deben ser distintos al activar la restricción");
        }
    }

    private long number(String field, String value, long min, long max) {
        if (value == null || !value.matches("[0-9]+")) {
            throw new IllegalArgumentException(field + ": debe ser un número entero entre " + min + " y " + max);
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed >= min && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) {
            // Una cadena fuera de la capacidad de long también es un error de entrada.
        }
        throw new IllegalArgumentException(field + ": debe estar entre " + min + " y " + max);
    }

    private LocalTime time(String field, String value) {
        if (value != null && value.matches("[0-9]{2}:[0-9]{2}")) {
            try {
                return LocalTime.parse(value);
            } catch (DateTimeParseException ignored) {
                // Mensaje de campo uniforme para horas fuera de rango y formatos inválidos.
            }
        }
        throw new IllegalArgumentException(field + ": debe tener formato HH:mm entre 00:00 y 23:59");
    }

    private static String policyKey(String name) {
        String key = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "tiempo bloqueo (min)" -> "tiempo de bloqueo (min)";
            case "longitud de contrasena minima" -> "longitud minima de contrasena";
            case "requiere cambio contrasena" -> "requiere cambio de contrasena";
            default -> key;
        };
    }
}
