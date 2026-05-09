package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.LogCargaAutomatica;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LogCargaAutomaticaRepository extends JpaRepository<LogCargaAutomatica, Long> {
    Optional<LogCargaAutomatica> findByNombreArchivo(String nombreArchivo);
    java.util.List<LogCargaAutomatica> findAllByOrderByFechaInicioDesc();
}
