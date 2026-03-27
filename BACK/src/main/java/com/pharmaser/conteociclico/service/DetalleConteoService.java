package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.repository.DetalleConteoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

import java.time.LocalDate;

@Service
public class DetalleConteoService {

    @Autowired
    private DetalleConteoRepository detalleConteoRepository;

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

    public Optional<DetalleConteo> getDetalleById(Integer id) {
        return detalleConteoRepository.findById(id);
    }

    public DetalleConteo saveDetalle(DetalleConteo detalle) {
        // Evitar duplicados si es un nuevo registro
        if (detalle.getId() == null) {
            List<DetalleConteo> existentes = detalleConteoRepository.findByIdUsuarioAndFechaRegistro(detalle.getIdUsuario(), detalle.getFechaRegistro());
            for (DetalleConteo e : existentes) {
                if (e.getIdMedicamento().equals(detalle.getIdMedicamento()) && 
                    e.getTipoConteo().equals(detalle.getTipoConteo()) &&
                    e.getCantidadContada() == null) {
                    return e; // Ya existe uno pendiente, retornamos el existente
                }
            }
        }
        return detalleConteoRepository.save(detalle);
    }

    public void deleteDetalle(Integer id) {
        detalleConteoRepository.deleteById(id);
    }
}
