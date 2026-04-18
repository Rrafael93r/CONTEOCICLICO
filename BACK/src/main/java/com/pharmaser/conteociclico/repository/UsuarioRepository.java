package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByUsuario(String usuario);
    Optional<Usuario> findByUsuarioIgnoreCase(String usuario);
    java.util.List<Usuario> findBySede(String sede);
    java.util.List<Usuario> findBySedeAndIdRol(String sede, Integer idRol);
}
