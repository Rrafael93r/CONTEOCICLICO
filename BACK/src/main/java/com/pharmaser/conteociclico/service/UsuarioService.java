package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<Usuario> getAllUsuarios() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> getUsuarioById(Integer id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> getUsuarioByUsername(String username) {
        return usuarioRepository.findByUsuarioIgnoreCase(username);
    }

    public Usuario saveUsuario(Usuario usuario) {
        // Encode password before saving ONLY if it's not already starting with $2a$ (BCrypt prefix)
        if (usuario.getContrasena() != null && !usuario.getContrasena().startsWith("$2a$")) {
            usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        }
        return usuarioRepository.save(usuario);
    }

    public Usuario updateUsuario(Integer id, Usuario usuario) {
        return usuarioRepository.findById(id).map(existingUser -> {
            if (usuario.getUsuario() != null) existingUser.setUsuario(usuario.getUsuario());
            if (usuario.getSede() != null) existingUser.setSede(usuario.getSede());
            if (usuario.getNumeroConteo() != null) existingUser.setNumeroConteo(usuario.getNumeroConteo());
            return usuarioRepository.save(existingUser);
        }).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public void deleteUsuario(Integer id) {
        usuarioRepository.deleteById(id);
    }
}
