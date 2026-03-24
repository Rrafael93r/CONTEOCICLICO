package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Inventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventarioRepository extends JpaRepository<Inventario, Integer> {
    Optional<Inventario> findByIdUsuarioAndIdMedicamento(Integer idUsuario, Integer idMedicamento);
}
