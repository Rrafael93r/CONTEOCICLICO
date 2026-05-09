package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsuarioIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        String roleName = resolveRoleName(usuario);
        return new User(
                usuario.getUsuario(),
                usuario.getContrasena(),
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
    }

    /**
     * Resuelve el nombre del rol para Spring Security.
     * Usa idRol como fuente primaria (valor numérico infalible) para evitar
     * dependencias de la cadena de texto almacenada en la tabla 'rol'.
     * Fallback: normaliza el nombre del rol si idRol es nulo.
     *
     * Mapeo: 1 = FARMACIA, 2 = CONTROL_DE_INVENTARIO, 3 = ADMINISTRADOR
     */
    private String resolveRoleName(Usuario usuario) {
        // Fuente primaria: idRol (entero, no depende del texto de la BD)
        if (usuario.getIdRol() != null) {
            return switch (usuario.getIdRol()) {
                case 3  -> "ADMINISTRADOR";
                case 2  -> "CONTROL_DE_INVENTARIO";
                default -> "FARMACIA";
            };
        }

        // Fallback: usar el nombre del rol de la relación JPA
        if (usuario.getRol() != null && usuario.getRol().getNombre() != null) {
            return usuario.getRol().getNombre()
                    .toUpperCase()
                    .replace(" ", "_")
                    .replace("Ó", "O").replace("Á", "A").replace("É", "E")
                    .replace("Í", "I").replace("Ú", "U");
        }

        return "FARMACIA"; // default seguro
    }
}
