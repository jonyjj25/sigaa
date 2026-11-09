package com.sigaa.security;

import com.sigaa.model.Usuario;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.SecurityIpService;
import com.sigaa.service.SecurityPolicyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtFilterTest {
    private JwtUtil jwtUtil;
    private UserDetailsService userDetailsService;
    private SecurityIpService securityIpService;
    private UsuarioRepository usuarios;
    private JwtFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        userDetailsService = mock(UserDetailsService.class);
        securityIpService = mock(SecurityIpService.class);
        usuarios = mock(UsuarioRepository.class);
        filter = new JwtFilter(jwtUtil, userDetailsService, securityIpService, usuarios, mock(SecurityPolicyService.class));
        when(securityIpService.checkAccess(anyString())).thenReturn(new SecurityIpService.AccessCheckResult(true, null));
        when(jwtUtil.validateToken("token-vigente")).thenReturn(true);
        when(jwtUtil.extractUsername("token-vigente")).thenReturn("cuenta@institucion.gob.pa");
        when(jwtUtil.extractLongClaim("token-vigente", "token_version")).thenReturn(4L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInactiveAccountEvenWithCryptographicallyValidToken() throws Exception {
        Usuario usuario = account(false, false, 4L);
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));

        Invocation invocation = invoke();

        assertThat(invocation.response().getStatus()).isEqualTo(401);
        assertThat(invocation.chainCalled()).isFalse();
    }

    @Test
    void rejectsBlockedAccountEvenWithCurrentTokenVersion() throws Exception {
        Usuario usuario = account(true, true, 4L);
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));

        Invocation invocation = invoke();

        assertThat(invocation.response().getStatus()).isEqualTo(401);
        assertThat(invocation.chainCalled()).isFalse();
    }

    @Test
    void rejectsSecondFactorPendingTokenAsAnAuthenticatedSession() throws Exception {
        when(jwtUtil.hasClaim("token-vigente", "2fa_pending")).thenReturn(true);

        Invocation invocation = invoke();

        assertThat(invocation.response().getStatus()).isEqualTo(401);
        assertThat(invocation.response().getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(invocation.response().getContentAsString()).contains("verificación", "está pendiente");
        assertThat(invocation.chainCalled()).isFalse();
    }

    @Test
    void allowsAuthenticationFlowDespiteStalePendingCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/2fa/authenticate");
        request.setServletPath("/api/auth/2fa/authenticate");
        request.setCookies(new Cookie("sigaa_access", "token-vigente"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> called.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(called).isTrue();
    }

    @Test
    void authenticatesOnlyUsableAccountWithMatchingTokenVersion() throws Exception {
        Usuario usuario = account(true, false, 4L);
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        when(userDetailsService.loadUserByUsername(usuario.getCorreo())).thenReturn(
                new User(usuario.getCorreo(), "hash", List.of(new SimpleGrantedAuthority("Documentos:Ver"))));

        Invocation invocation = invoke();

        assertThat(invocation.response().getStatus()).isEqualTo(200);
        assertThat(invocation.chainCalled()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(usuario.getCorreo());
    }

    @Test
    void onlyThePublicSessionLookupSkipsAuthentication() {
        MockHttpServletRequest publicLookup = new MockHttpServletRequest("GET", "/api/asistencia/sesiones/SESION-ABC123");
        publicLookup.setServletPath("/api/asistencia/sesiones/SESION-ABC123");
        MockHttpServletRequest attendees = new MockHttpServletRequest("GET", "/api/asistencia/sesiones/SESION-ABC123/asistentes");
        attendees.setServletPath("/api/asistencia/sesiones/SESION-ABC123/asistentes");
        MockHttpServletRequest guests = new MockHttpServletRequest("GET", "/api/asistencia/sesiones/SESION-ABC123/invitados");
        guests.setServletPath("/api/asistencia/sesiones/SESION-ABC123/invitados");

        assertThat(filter.shouldNotFilter(publicLookup)).isTrue();
        assertThat(filter.shouldNotFilter(attendees)).isFalse();
        assertThat(filter.shouldNotFilter(guests)).isFalse();
    }

    private Invocation invoke() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-vigente");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> called.set(true);
        filter.doFilter(request, response, chain);
        return new Invocation(response, called.get());
    }

    private Usuario account(boolean active, boolean blocked, long tokenVersion) {
        Usuario usuario = new Usuario();
        usuario.setCorreo("cuenta@institucion.gob.pa");
        usuario.setActivo(active);
        usuario.setBloqueado(blocked);
        usuario.setTokenVersion(tokenVersion);
        return usuario;
    }

    private record Invocation(MockHttpServletResponse response, boolean chainCalled) {}
}
