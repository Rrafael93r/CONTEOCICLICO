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
    private MedicamentoService medicamentoService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("ALTER TABLE detalleconteo ADD COLUMN IF NOT EXISTS lote VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE detalleconteo ADD COLUMN IF NOT EXISTS fechavencimiento DATE");
        } catch (Exception e) {
            // Ignorar si ya existen o si el motor no soporta IF NOT EXISTS
        }
    }

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
        // Permitir duplicados si traen lote o si el existente ya tiene lote (lotes múltiples)
        if (detalle.getId() == null || detalle.getId() == 0) {
            List<DetalleConteo> existentes = detalleConteoRepository
                    .findByIdUsuarioAndFechaRegistro(detalle.getIdUsuario(), detalle.getFechaRegistro());
            for (DetalleConteo e : existentes) {
                // Si la molécula ya existe hoy, está pendiente, y NO tiene lote, 
                // entonces sí es un duplicado del registro base.
                // Pero si el nuevo registro trae lote, lo dejamos pasar como lote extra.
                if (detalle.getIdPersonalizado() == null && e.getIdPersonalizado() == null &&
                        e.getIdMedicamento().equals(detalle.getIdMedicamento()) &&
                        e.getCantidadContada() == null &&
                        (detalle.getLote() == null || detalle.getLote().isEmpty()) &&
                        (e.getLote() == null || e.getLote().isEmpty())) {
                    return e;
                }
            }
        }
        return detalleConteoRepository.save(detalle);
    }

    @org.springframework.transaction.annotation.Transactional
    public List<DetalleConteo> saveAllDetalles(List<DetalleConteo> detalles) {
        if (detalles.isEmpty()) return detalles;

        for (DetalleConteo item : detalles) {
            try {
                // Sincronizar con tabla medicamento si hay cantidad contada
                if (item.getCantidadContada() != null && item.getIdMedicamento() != null) {
                    medicamentoService.marcarComoContado(item.getIdMedicamento(), item.getCantidadContada().doubleValue());
                }

                if (item.getId() == null || item.getId() == 0) {
                    // INSERT MANUAL
                    jdbcTemplate.update(
                        "INSERT INTO detalleconteo (idmedicamento, idusuario, cantidadcontada, cantidadactual, fecharegistro, horaregistro, tipoconteo, idpersonalizado, lote, fechavencimiento) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        item.getIdMedicamento(), item.getIdUsuario(), item.getCantidadContada(), item.getCantidadActual(), 
                        item.getFechaRegistro(), item.getHoraRegistro(), item.getTipoConteo(), item.getIdPersonalizado(),
                        item.getLote(), item.getFechaVencimiento()
                    );
                } else {
                    // UPDATE MANUAL
                    jdbcTemplate.update(
                        "UPDATE detalleconteo SET cantidadcontada = ?, horaregistro = ?, lote = ?, fechavencimiento = ? WHERE id = ?",
                        item.getCantidadContada(), item.getHoraRegistro(), item.getLote(), item.getFechaVencimiento(), item.getId()
                    );
                }
            } catch (Exception e) {
                // Si falla uno, intentamos con el siguiente pero registramos el error
                System.err.println("Error guardando detalle: " + e.getMessage());
            }
        }
        return detalles;
    }

    public void deleteDetalle(@NonNull Integer id) {
        detalleConteoRepository.deleteById(id);
    }
}
