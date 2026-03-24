package com.pharmaser.conteociclico.config;

import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataLoader {

    @Bean
    public CommandLineRunner initData(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // El proceso de carga masiva de usuarios ya fue realizado exitosamente.
            // Se mantiene solo el chequeo básico del administrador principal por seguridad.
            if (usuarioRepository.findByUsuario("admin").isEmpty()) {
                Usuario admin = new Usuario();
                admin.setUsuario("admin");
                admin.setContrasena(passwordEncoder.encode("admin"));
                admin.setSede("CENTRAL");
                admin.setNumeroConteo(0);
                admin.setIdRol(3); // Administrador
                usuarioRepository.save(admin);
            }
        };
    }
}
