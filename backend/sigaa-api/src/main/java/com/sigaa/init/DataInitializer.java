package com.sigaa.init;

import com.sigaa.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner hashExistingPasswords(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            usuarioRepository.findAll().forEach(u -> {
                String pw = u.getPassword();
                if (pw != null && !pw.startsWith("$2a$") && !pw.startsWith("$2b$") && !pw.startsWith("$2y$")) {
                    u.setPassword(passwordEncoder.encode(pw));
                    usuarioRepository.save(u);
                }
            });
        };
    }
}
