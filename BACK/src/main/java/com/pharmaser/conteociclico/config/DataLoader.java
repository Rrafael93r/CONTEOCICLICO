package com.pharmaser.conteociclico.config;

import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataLoader {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData(UsuarioRepository usuarioRepository) {
        return args -> {
            if (usuarioRepository.findByUsuario("admin").isEmpty()) {
                Usuario admin = new Usuario();
                admin.setUsuario("admin");
                admin.setContrasena(passwordEncoder.encode("admin"));
                admin.setSede("BOGOTA");
                admin.setNumeroConteo(0);
                usuarioRepository.save(admin);
            }
        };
    }
}
