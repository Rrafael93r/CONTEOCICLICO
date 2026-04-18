package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.SistemaConfiguracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SistemaConfiguracionRepository extends JpaRepository<SistemaConfiguracion, String> {
}
