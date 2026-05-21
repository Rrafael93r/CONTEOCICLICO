package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<Usuario> getAllUsuarios() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> getUsuarioById(@NonNull Integer id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> getUsuarioByUsername(String username) {
        return usuarioRepository.findByUsuarioIgnoreCase(username);
    }

    public Usuario saveUsuario(Usuario usuario) {
        // Encode password only if it's not already a BCrypt hash.
        // BCrypt hashes can start with $2a$, $2b$ or $2y$ — all three variants
        // are produced by different versions of the spec but are equally valid.
        if (usuario.getContrasena() != null
                && !usuario.getContrasena().startsWith("$2a$")
                && !usuario.getContrasena().startsWith("$2b$")
                && !usuario.getContrasena().startsWith("$2y$")) {
            usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        }
        return usuarioRepository.save(usuario);
    }

    public Usuario updateUsuario(@NonNull Integer id, Usuario usuario) {
        return usuarioRepository.findById(id).map((@NonNull Usuario existingUser) -> {
            if (usuario.getUsuario() != null) existingUser.setUsuario(usuario.getUsuario());
            if (usuario.getSede() != null) existingUser.setSede(usuario.getSede());
            if (usuario.getFechaBloqueExtra() != null) {
                existingUser.setFechaBloqueExtra(usuario.getFechaBloqueExtra());
            }
            return usuarioRepository.save(existingUser);
        }).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public void deleteUsuario(@NonNull Integer id) {
        usuarioRepository.deleteById(id);
    }
}
