package com.sigaa.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.Authentication;

/** Los cargos institucionales no conceden administración técnica. */
public final class InstitutionalRoles {
    private InstitutionalRoles() {}

    public static String canonical(String name) {
        String value = Normalizer.normalize(name == null ? "" : name.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "directora", "director" -> "Director";
            case "subdirectora", "subdirector" -> "Subdirector";
            case "secretaria", "secretario" -> "Secretaria";
            case "superadmin" -> "Superadmin";
            case "administrador" -> "Administrador";
            case "jefe de unidad", "jefa de unidad" -> "Jefe de Unidad";
            case "miembro" -> "Miembro";
            default -> name == null ? "" : name.trim();
        };
    }

    public static boolean global(String name) {
        return Set.of("Superadmin", "Director", "Subdirector", "Secretaria").contains(canonical(name));
    }

    public static boolean unit(String name) {
        return Set.of("Jefe de Unidad", "Miembro").contains(canonical(name));
    }

    public static boolean operational(String name) {
        return Set.of("Superadmin", "Director").contains(canonical(name));
    }

    public static boolean operational(Authentication authentication) {
        return authenticated(authentication)
            && authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                .filter(name -> name.startsWith("ROLE_"))
                .anyMatch(name -> operational(name.substring(5)));
    }

    public static boolean retired(String name) {
        return "Administrador".equals(canonical(name));
    }

    public static boolean hasRole(Authentication authentication, String role) {
        return authenticated(authentication) && authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(name -> name.startsWith("ROLE_"))
            .anyMatch(name -> canonical(role).equals(canonical(name.substring(5))));
    }

    /** La subdirección tiene funciones operativas, pero nunca un alcance global por su cargo. */
    public static boolean scopedSubdirector(Authentication authentication) {
        return hasRole(authentication, "Subdirector") && !operational(authentication);
    }

    private static boolean authenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }
}
