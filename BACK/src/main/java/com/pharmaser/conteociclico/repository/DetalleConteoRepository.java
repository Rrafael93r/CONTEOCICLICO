package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.DetalleConteo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DetalleConteoRepository extends JpaRepository<DetalleConteo, Integer> {
    @org.springframework.data.jpa.repository.Query("SELECT d FROM DetalleConteo d WHERE d.idUsuario = :idUsuario AND d.fechaRegistro = :fechaRegistro")
    List<DetalleConteo> findByIdUsuarioAndFechaRegistro(@org.springframework.data.repository.query.Param("idUsuario") Integer idUsuario, @org.springframework.data.repository.query.Param("fechaRegistro") java.time.LocalDate fechaRegistro);
    List<DetalleConteo> findByIdUsuario(Integer idUsuario);
    List<DetalleConteo> findByFechaRegistro(LocalDate fechaRegistro);
    List<DetalleConteo> findByFechaRegistroBetween(LocalDate start, LocalDate end);
    List<DetalleConteo> findByIdUsuarioAndFechaRegistroBetween(Integer idUsuario, LocalDate start, LocalDate end);
}
