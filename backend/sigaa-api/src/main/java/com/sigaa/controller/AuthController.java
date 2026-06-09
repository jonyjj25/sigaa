package com.sigaa.controller;

import com.sigaa.dto.LoginRequest;
import com.sigaa.dto.LoginResponse;
import com.sigaa.dto.UserInfoResponse;
import com.sigaa.model.Usuario;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(request.getCorreo());

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (Boolean.TRUE.equals(usuario.getActivo()) && passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
                String token = jwtUtil.generateToken(usuario.getCorreo(), usuario.getRoles());
                LoginResponse response = new LoginResponse();
                response.setStatus("success");
                response.setToken(token);
                response.setCorreo(usuario.getCorreo());
                response.setNombre(usuario.getNombre());
                response.setRoles(usuario.getRoles());
                return ResponseEntity.ok(response);
            }
        }

        LoginResponse errorResponse = new LoginResponse();
        errorResponse.setStatus("error");
        errorResponse.setMessage("Correo o contraseña incorrectos");
        return ResponseEntity.status(401).body(errorResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(authentication.getName());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        Usuario usuario = usuarioOpt.get();
        UserInfoResponse response = new UserInfoResponse(usuario.getNombre(), usuario.getCorreo(), usuario.getRoles());
        return ResponseEntity.ok(response);
    }
}
