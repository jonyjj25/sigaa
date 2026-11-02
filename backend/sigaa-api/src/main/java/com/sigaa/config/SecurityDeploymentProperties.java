package com.sigaa.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Parámetros técnicos de despliegue; no se leen de políticas ni listas históricas. */
@ConfigurationProperties(prefix = "app.security")
public record SecurityDeploymentProperties(
        @DefaultValue("300000") long twoFactorTokenExpiryMs,
        Boolean ipRestrictionEnabled,
        @DefaultValue("false") boolean ipWhitelistActive,
        List<String> ipWhitelist, List<String> ipBlacklist) {
    // El código de verificación existente vence a los cinco minutos.
    public static final long MAX_TWO_FACTOR_TOKEN_EXPIRY_MS = 5 * 60_000L;

    public SecurityDeploymentProperties {
        if (twoFactorTokenExpiryMs < 1000 || twoFactorTokenExpiryMs > MAX_TWO_FACTOR_TOKEN_EXPIRY_MS) {
            throw new IllegalArgumentException("app.security.two-factor-token-expiry-ms debe estar entre 1000 y 300000 ms");
        }
        ipWhitelist = rules(ipWhitelist);
        ipBlacklist = rules(ipBlacklist);
    }

    public boolean isIpAllowed(String ip) {
        if (!Boolean.TRUE.equals(ipRestrictionEnabled)) return true;
        if (ipBlacklist.stream().anyMatch(rule -> matches(rule, ip))) return false;
        return !ipWhitelistActive || ipWhitelist.stream().anyMatch(rule -> matches(rule, ip));
    }

    private static List<String> rules(List<String> values) {
        List<String> rules = values == null ? List.of() : values.stream().map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
        for (String rule : rules) {
            String[] bounds = rule.split("-", -1);
            if (bounds.length > 2 || ipv4(bounds[0]) < 0
                    || (bounds.length == 2 && (ipv4(bounds[1]) < 0 || ipv4(bounds[0]) > ipv4(bounds[1])))) {
                throw new IllegalArgumentException("Las listas IP requieren IPv4 o rangos IPv4 inicio-fin válidos");
            }
        }
        return rules;
    }

    private static boolean matches(String rule, String ip) {
        String[] bounds = rule.split("-", -1);
        if (bounds.length == 1) return rule.equals(ip);
        long address = ipv4(ip);
        return address >= ipv4(bounds[0]) && address <= ipv4(bounds[1]);
    }

    private static long ipv4(String ip) {
        if (ip == null) return -1;
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) return -1;
        long result = 0;
        for (String octet : octets) {
            if (!octet.matches("[0-9]{1,3}")) return -1;
            int value = Integer.parseInt(octet);
            if (value > 255) return -1;
            result = result * 256 + value;
        }
        return result;
    }
}
