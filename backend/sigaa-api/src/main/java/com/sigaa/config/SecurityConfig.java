package com.sigaa.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.sigaa.security.JwtFilter;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
					// Actuator se sirve en un puerto de gestión separado. En producción ese
					// puerto existe únicamente en la red privada de Docker.
					.requestMatchers("/actuator", "/actuator/**", "/livez", "/readyz").permitAll()
                    .requestMatchers(HttpMethod.POST,
                            "/api/auth/login",
                            "/api/auth/2fa/authenticate",
                            "/api/auth/forgot-password",
                            "/api/auth/resend-reset-code",
                            "/api/auth/verify-reset-code",
                            "/api/auth/reset-password",
                            "/api/auth/logout").permitAll()
                    .requestMatchers("/api/auth/me", "/api/auth/me/**").authenticated()
                    .requestMatchers("/api/asistencia/invitado").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/asistencia/sesiones/*").permitAll()
                    .requestMatchers("/uploads/fotografias/**").permitAll()
                    .requestMatchers("/uploads/tareas/**").denyAll()
                    .requestMatchers("/uploads/notas/**").denyAll()
                    .requestMatchers("/uploads/documentos/**", "/uploads/actas/**", "/uploads/reportes/**").denyAll()
                    .requestMatchers("/api/config").permitAll()
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Token inválido o ausente\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"No tiene permisos para acceder a este recurso\"}");
                })
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Solo las fotografías de perfil son recursos públicos. Los archivos
                // privados se entregan mediante los endpoints autorizados de cada módulo.
                registry.addResourceHandler("/uploads/fotografias/**")
                        .addResourceLocations("file:uploads/fotografias/");
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", properties.toConfiguration());
        return source;
    }
}
