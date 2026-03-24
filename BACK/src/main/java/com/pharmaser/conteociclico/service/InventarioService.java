package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Inventario;
import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.dto.InventarioImportDTO;
import com.pharmaser.conteociclico.repository.InventarioRepository;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class InventarioService {

    @Autowired
    private InventarioRepository inventarioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private MedicamentoRepository medicamentoRepository;

    public List<Inventario> getAllInventarios() {
        return inventarioRepository.findAll();
    }

    public void importFromExternalData(List<InventarioImportDTO> items) {
        for (InventarioImportDTO item : items) {
            // Buscamos el usuario por su sede
            usuarioRepository.findBySede(item.getSede()).ifPresent(usuario -> {
                // Buscamos el medicamento por su PLU
                medicamentoRepository.findByPlu(item.getPlu()).ifPresent(med -> {
                    
                    // Buscamos si ya existe un registro para este usuario y medicamento
                    Inventario inv = inventarioRepository
                        .findByIdUsuarioAndIdMedicamento(usuario.getId(), med.getId())
                        .orElse(new Inventario());
                    
                    inv.setIdUsuario(usuario.getId());
                    inv.setIdMedicamento(med.getId());
                    inv.setCantidadActual(item.getCantidad());
                    inv.setFechaRegistro(LocalDate.now());
                    inv.setHoraRegistro(LocalTime.now());
                    
                    inventarioRepository.save(inv);
                });
            });
        }
    }

    public Optional<Inventario> getInventarioById(Integer id) {
        return inventarioRepository.findById(id);
    }

    public Inventario saveInventario(Inventario inventario) {
        return (Inventario) inventarioRepository.save(inventario);
    }

    public void deleteInventario(Integer id) {
        inventarioRepository.deleteById(id);
    }
}
