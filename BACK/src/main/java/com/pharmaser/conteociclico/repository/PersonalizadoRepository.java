package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Personalizado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonalizadoRepository extends JpaRepository<Personalizado, Integer> {
    List<Personalizado> findByIdUsuario(Integer idUsuario);
}
