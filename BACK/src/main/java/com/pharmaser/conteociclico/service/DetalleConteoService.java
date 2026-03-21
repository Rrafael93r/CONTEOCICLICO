package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.repository.DetalleConteoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class DetalleConteoService {

    @Autowired
    private DetalleConteoRepository detalleConteoRepository;

    public List<DetalleConteo> getAllDetalles() {
        return detalleConteoRepository.findAll();
    }

    public Optional<DetalleConteo> getDetalleById(Integer id) {
        return detalleConteoRepository.findById(id);
    }

    public DetalleConteo saveDetalle(DetalleConteo detalle) {
        return detalleConteoRepository.save(detalle);
    }

    public void deleteDetalle(Integer id) {
        detalleConteoRepository.deleteById(id);
    }
}
