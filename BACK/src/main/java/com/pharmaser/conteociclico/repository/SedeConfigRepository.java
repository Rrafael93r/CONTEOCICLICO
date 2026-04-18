package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.SedeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SedeConfigRepository extends JpaRepository<SedeConfig, Integer> {
    Optional<SedeConfig> findByCodigoSede(String codigoSede);
}
