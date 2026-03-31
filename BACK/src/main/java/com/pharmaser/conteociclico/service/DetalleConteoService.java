package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.repository.DetalleConteoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;
import java.util.List;
import java.util.Optional;

import java.time.LocalDate;

@Service
public class DetalleConteoService {

    @Autowired
    private DetalleConteoRepository detalleConteoRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public List<DetalleConteo> getDetallesByUsuarioYFecha(Integer idUsuario, LocalDate fecha) {
        return detalleConteoRepository.findByIdUsuarioAndFechaRegistro(idUsuario, fecha);
    }

    public List<DetalleConteo> getDetallesByUsuario(Integer idUsuario) {
        return detalleConteoRepository.findByIdUsuario(idUsuario);
    }

    public List<DetalleConteo> getDetallesByFecha(LocalDate fecha) {
        return detalleConteoRepository.findByFechaRegistro(fecha);
    }

    public List<DetalleConteo> getAllDetalles() {
        return detalleConteoRepository.findAll();
    }

    public List<DetalleConteo> getDetallesByRango(LocalDate start, LocalDate end) {
        return detalleConteoRepository.findByFechaRegistroBetween(start, end);
    }

    public List<DetalleConteo> getDetallesByUsuarioYRango(Integer idUsuario, LocalDate start, LocalDate end) {
        return detalleConteoRepository.findByIdUsuarioAndFechaRegistroBetween(idUsuario, start, end);
    }

    public Optional<DetalleConteo> getDetalleById(@NonNull Integer id) {
        return detalleConteoRepository.findById(id);
    }

    public DetalleConteo saveDetalle(@NonNull DetalleConteo detalle) {
        // Evitar duplicados si es un nuevo registro
        if (detalle.getId() == null) {
            List<DetalleConteo> existentes = detalleConteoRepository
                    .findByIdUsuarioAndFechaRegistro(detalle.getIdUsuario(), detalle.getFechaRegistro());
            for (DetalleConteo e : existentes) {
                // Si es un conteo cíclico (sin ID personalizado), evitamos duplicar la misma
                // molécula el mismo día si está pendiente
                if (detalle.getIdPersonalizado() == null && e.getIdPersonalizado() == null &&
                        e.getIdMedicamento().equals(detalle.getIdMedicamento()) &&
                        e.getCantidadContada() == null) {
                    return e;
                }
                // Si es un conteo personalizado, evitamos duplicar la misma ORDEN de asignación
                // si ya está en la tabla hoy
                if (detalle.getIdPersonalizado() != null &&
                        detalle.getIdPersonalizado().equals(e.getIdPersonalizado()) &&
                        e.getCantidadContada() == null) {
                    return e;
                }
            }
        }
        return detalleConteoRepository.save(detalle);
    }

    @org.springframework.transaction.annotation.Transactional
    public List<DetalleConteo> saveAllDetalles(List<DetalleConteo> detalles) {
        if (detalles.isEmpty())
            return detalles;

        String insertSql = "INSERT INTO detalleconteo (idmedicamento, idusuario, cantidadcontada, cantidadactual, fecharegistro, horaregistro, tipoconteo, idpersonalizado) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql = "UPDATE detalleconteo SET cantidadcontada = ?, horaregistro = ? WHERE id = ?";

        java.util.List<DetalleConteo> toInsert = new java.util.ArrayList<>();
        java.util.List<DetalleConteo> toUpdate = new java.util.ArrayList<>();

        for (DetalleConteo d : detalles) {
            if (d.getId() == null) {
                toInsert.add(d);
            } else {
                toUpdate.add(d);
            }
        }

        if (!toInsert.isEmpty()) {
            jdbcTemplate.batchUpdate(insertSql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(@NonNull java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    DetalleConteo item = toInsert.get(i);
                    ps.setObject(1, item.getIdMedicamento());
                    ps.setObject(2, item.getIdUsuario());
                    ps.setObject(3, item.getCantidadContada());
                    ps.setObject(4, item.getCantidadActual());
                    ps.setObject(5, item.getFechaRegistro());
                    ps.setObject(6, item.getHoraRegistro());
                    ps.setString(7, item.getTipoConteo());
                    ps.setObject(8, item.getIdPersonalizado());
                }

                @Override
                public int getBatchSize() {
                    return toInsert.size();
                }
            });
        }

        if (!toUpdate.isEmpty()) {
            jdbcTemplate.batchUpdate(updateSql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(@NonNull java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    DetalleConteo item = toUpdate.get(i);
                    ps.setObject(1, item.getCantidadContada());
                    ps.setObject(2, item.getHoraRegistro());
                    ps.setObject(3, item.getId());
                }

                @Override
                public int getBatchSize() {
                    return toUpdate.size();
                }
            });
        }

        return detalles;
    }

    public void deleteDetalle(@NonNull Integer id) {
        detalleConteoRepository.deleteById(id);
    }
}
