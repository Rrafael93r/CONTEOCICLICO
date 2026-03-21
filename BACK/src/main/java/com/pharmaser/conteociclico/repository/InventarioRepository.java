package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Inventario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventarioRepository extends JpaRepository<Inventario, Integer> {
}
