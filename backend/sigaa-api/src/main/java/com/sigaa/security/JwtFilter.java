package com.sigaa.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import com.sigaa.service.SecurityIpService;
import com.sigaa.repository.UsuarioRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "/api/auth/login",
            "/api/auth/2fa/authenticate",
            "/api/auth/forgot-password",
            "/api/auth/resend-reset-code",
            "/api/auth/verify-reset-code",
            "/api/auth/reset-password"
    );

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final SecurityIpService securityIpService;
    private final UsuarioRepository usuarioRepository;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService, SecurityIpService securityIpService, UsuarioRepository usuarioRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.securityIpService = securityIpService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        return HttpMethod.OPTIONS.matches(method)
                || (HttpMethod.POST.matches(method) && PUBLIC_AUTH_ENDPOINTS.contains(path))
                || "/actuator".equals(path)
                || path.startsWith("/actuator/")
                || "/livez".equals(path)
                || "/readyz".equals(path)
                || path.startsWith("/uploads/fotografias/")
                // Estas API públicas no eluden la restricción si llega una sesión.
                // Los subrecursos de asistencia conservan sus controles habituales.
                || (sessionToken(request) == null && (
                    "/api/config".equals(path)
                    || (HttpMethod.POST.matches(method) && "/api/asistencia/invitado".equals(path))
                    || (HttpMethod.GET.matches(method) && path.matches("^/api/asistencia/sesiones/[^/]+$"))));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = sessionToken(request);

        if (token != null && !token.isBlank()) {
            var access = securityIpService.checkAccess(request.getRemoteAddr());
            if (!access.allowed()) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Forbidden", "Acceso restringido por política de seguridad");
                return;
            }
            if (jwtUtil.validateToken(token)) {
                if (jwtUtil.hasClaim(token, "2fa_pending")) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Unauthorized", "La verificación de segundo factor está pendiente");
                    return;
                }
                String username = jwtUtil.extractUsername(token);
                var usuario = usuarioRepository.findByCorreoIgnoreCase(username);
                if (usuario.isEmpty()
                        || !Boolean.TRUE.equals(usuario.get().getActivo())
                        || Boolean.TRUE.equals(usuario.get().getBloqueado())
                        || usuario.get().getTokenVersion() != jwtUtil.extractLongClaim(token, "token_version")) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Unauthorized", "La sesión ya no es válida");
                    return;
                }
                if (usuario.get().isTemporaryPasswordExpired() && !isLogout(request)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "TEMPORARY_PASSWORD_EXPIRED", "La contraseña temporal expiró. Solicita un nuevo restablecimiento.");
                    return;
                }
                if (usuario.get().isPasswordExpired() && !isLogout(request)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "PASSWORD_EXPIRED",
                            "La contraseña venció. Usa Recuperar contraseña para establecer una nueva.");
                    return;
                }
                if (Boolean.TRUE.equals(usuario.get().getCambioContrasenaRequerido())
                        && !allowsRequiredPasswordChange(request)) {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN,
                            "PASSWORD_CHANGE_REQUIRED", "Debes cambiar la contraseña temporal para continuar.");
                    return;
                }
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean allowsRequiredPasswordChange(HttpServletRequest request) {
        String path = request.getServletPath();
        return isLogout(request) || (HttpMethod.GET.matches(request.getMethod())
                && ("/api/auth/me".equals(path) || "/api/auth/me/password-policy".equals(path)))
                || (HttpMethod.PATCH.matches(request.getMethod()) && "/api/auth/me/required-password".equals(path));
    }

    private boolean isLogout(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && "/api/auth/logout".equals(request.getServletPath());
    }

    private String sessionToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) return authHeader.substring(7);
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("sigaa_access".equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\",\"code\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
