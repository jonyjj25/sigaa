package com.sigaa.config;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

/** Configuración técnica del despliegue; no consulta políticas ni listas de la BD. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins, List<String> allowedMethods,
                             List<String> allowedHeaders, List<String> exposedHeaders,
                             boolean allowCredentials, long maxAge) {
    public CorsProperties {
        allowedOrigins = clean(allowedOrigins);
        allowedMethods = clean(allowedMethods);
        allowedHeaders = clean(allowedHeaders);
        exposedHeaders = clean(exposedHeaders);
    }

    CorsConfiguration toConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setExposedHeaders(exposedHeaders);
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);
        // Rechaza una configuración insegura al arrancar, antes de atender peticiones.
        configuration.validateAllowCredentials();
        if (maxAge < 0) throw new IllegalArgumentException("app.cors.max-age no puede ser negativo");
        for (String origin : allowedOrigins) validateOrigin(origin);
        if (allowedMethods.stream().anyMatch(method -> !Set.of(
                "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS").contains(method))) {
            throw new IllegalArgumentException("app.cors.allowed-methods contiene un método no permitido");
        }
        return configuration;
    }

    private static void validateOrigin(String origin) {
        if ("*".equals(origin)) return; // Spring ya rechaza '*' junto a credenciales.
        try {
            URI uri = URI.create(origin);
            if (uri.getScheme() != null && Set.of("http", "https", "capacitor").contains(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getRawPath().isEmpty() && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && !origin.contains("*")) return;
        } catch (IllegalArgumentException ignored) {
            // El mensaje no expone valores del despliegue.
        }
        throw new IllegalArgumentException("app.cors.allowed-origins requiere orígenes exactos sin rutas ni comodines");
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }
}
