package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.DetalleConteo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DetalleConteoRepository extends JpaRepository<DetalleConteo, Integer> {
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM detalleconteo WHERE idusuario = ?1 AND fecharegistro = ?2", nativeQuery = true)
    List<DetalleConteo> findByIdUsuarioAndFechaRegistro(Integer idUsuario, java.time.LocalDate fechaRegistro);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM detalleconteo WHERE idusuario = ?1", nativeQuery = true)
    List<DetalleConteo> findByIdUsuario(Integer idUsuario);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM detalleconteo WHERE fecharegistro = ?1", nativeQuery = true)
    List<DetalleConteo> findByFechaRegistro(LocalDate fechaRegistro);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM detalleconteo WHERE fecharegistro BETWEEN ?1 AND ?2", nativeQuery = true)
    List<DetalleConteo> findByFechaRegistroBetween(LocalDate start, LocalDate end);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM detalleconteo WHERE idusuario = ?1 AND fecharegistro BETWEEN ?2 AND ?3", nativeQuery = true)
    List<DetalleConteo> findByIdUsuarioAndFechaRegistroBetween(Integer idUsuario, LocalDate start, LocalDate end);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "medicamento", "usuario" })
    @org.springframework.lang.NonNull
    List<DetalleConteo> findAll();

    @org.springframework.data.jpa.repository.Query("SELECT d FROM DetalleConteo d JOIN d.usuario u WHERE u.sede = :sede AND d.fechaRegistro = :fecha")
    List<DetalleConteo> findBySedeAndFecha(@org.springframework.data.repository.query.Param("sede") String sede, @org.springframework.data.repository.query.Param("fecha") LocalDate fecha);
}
