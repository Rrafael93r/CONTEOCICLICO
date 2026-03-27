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
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

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

    @Transactional
    public void importFromExternalData(List<InventarioImportDTO> items) {
        System.out.println("Iniciando AUTO-SINCRONIZACIÓN de saldos (" + items.size() + " referencias)...");
        
        // 1. Limpieza de saldos previos
        inventarioRepository.deleteAllInBatch();

        // 2. Cargamos usuarios para normalizar sedes
        List<Usuario> usuarios = usuarioRepository.findAll();
        Map<String, Integer> sedeToIdMap = new HashMap<>();
        for (Usuario u : usuarios) {
            if (u.getSede() != null) {
                try {
                    String norm = String.valueOf(Integer.parseInt(u.getSede().trim()));
                    sedeToIdMap.put(norm, u.getId());
                } catch (Exception e) {
                    sedeToIdMap.put(u.getSede().trim(), u.getId());
                }
            }
        }

        // 3. Cargamos catálogo de medicamentos para extraer la cantidad actual
        List<Medicamento> medicamentos = medicamentoRepository.findAll();
        Map<String, Medicamento> pluSedeToMedMap = new HashMap<>();
        for (Medicamento m : medicamentos) {
            if (m.getPlu() != null && m.getIdUsuario() != null) {
                pluSedeToMedMap.put(m.getPlu() + "_" + m.getIdUsuario(), m);
            }
        }

        List<Inventario> toSave = new ArrayList<>();
        LocalDate now = LocalDate.now();
        LocalTime time = LocalTime.now();

        for (InventarioImportDTO item : items) {
            if (item.getPlu() == null || item.getSede() == null) continue;

            // Normalización de sede del Excel
            String normSede;
            try { normSede = String.valueOf(Integer.parseInt(item.getSede().trim())); }
            catch (Exception e) { normSede = item.getSede().trim(); }
            
            Integer idUsuario = sedeToIdMap.get(normSede);
            if (idUsuario == null) continue;

            // Buscamos el medicamento para esta sede y extraemos su inventario quemado
            Medicamento med = pluSedeToMedMap.get(item.getPlu() + "_" + idUsuario);
            if (med == null) continue;

            Inventario inv = new Inventario();
            inv.setIdUsuario(idUsuario);
            inv.setIdMedicamento(med.getId());
            // TRAEMOS LA CANTIDAD DIRECTAMENTE DE LA TABLA MEDICAMENTO
            inv.setCantidadActual(med.getInventario() != null ? med.getInventario() : 0);
            inv.setFechaRegistro(now);
            inv.setHoraRegistro(time);
            
            toSave.add(inv);
        }
        
        inventarioRepository.saveAll(toSave);
        System.out.println("Auto-sincronización completada exitosamente. " + toSave.size() + " saldos actualizados desde el catálogo.");
    }

    public Optional<Inventario> getInventarioById(Integer id) {
        return inventarioRepository.findById(id);
    }

    public Inventario saveInventario(Inventario inventario) {
        return inventarioRepository.save(inventario);
    }

    public void deleteInventario(Integer id) {
        inventarioRepository.deleteById(id);
    }
}
