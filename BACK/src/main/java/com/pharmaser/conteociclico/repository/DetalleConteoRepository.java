package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.DetalleConteo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DetalleConteoRepository extends JpaRepository<DetalleConteo, Integer> {
    List<DetalleConteo> findByIdUsuarioAndFechaRegistro(Integer idUsuario, LocalDate fechaRegistro);
}
