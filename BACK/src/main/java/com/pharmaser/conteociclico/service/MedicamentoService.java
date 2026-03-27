package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.dto.MedicamentoImportDTO;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class MedicamentoService {

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private com.pharmaser.conteociclico.repository.UsuarioRepository usuarioRepository;

    public List<Medicamento> getAllMedicamentos() {
        return medicamentoRepository.findAll();
    }

    @org.springframework.transaction.annotation.Transactional
    public void bulkUpdateInventory(List<java.util.Map<String, Object>> items) {
        System.out.println("Iniciando ACTUALIZACIÓN directa de saldos en catálogo...");
        
        // 1. Cargamos usuarios para normalizar sedes
        List<com.pharmaser.conteociclico.model.Usuario> usuarios = usuarioRepository.findAll();
        java.util.Map<String, Integer> sedeToIdMap = new java.util.HashMap<>();
        for (com.pharmaser.conteociclico.model.Usuario u : usuarios) {
            if (u.getSede() != null) {
                try {
                    String norm = String.valueOf(Integer.parseInt(u.getSede().trim()));
                    sedeToIdMap.put(norm, u.getId());
                } catch (Exception e) {
                    sedeToIdMap.put(u.getSede().trim(), u.getId());
                }
            }
        }

        // 2. Cargamos catálogo actual
        List<Medicamento> currentCatalog = medicamentoRepository.findAll();
        java.util.Map<String, Medicamento> medMap = new java.util.HashMap<>();
        for (Medicamento m : currentCatalog) {
            if (m.getPlu() != null && m.getIdUsuario() != null) {
                medMap.put(m.getPlu() + "_" + m.getIdUsuario(), m);
            }
        }

        java.util.List<Medicamento> toUpdate = new java.util.ArrayList<>();

        for (java.util.Map<String, Object> item : items) {
            String plu = (String) item.get("plu");
            String sede = (String) item.get("sede");
            Integer cantidad = (Integer) item.get("cantidad");

            if (plu == null || sede == null) continue;

            // Normalización de sede
            String normSede;
            try { normSede = String.valueOf(Integer.parseInt(sede.trim())); }
            catch (Exception e) { normSede = sede.trim(); }
            
            Integer idUsuario = sedeToIdMap.get(normSede);
            if (idUsuario == null) continue;

            Medicamento med = medMap.get(plu + "_" + idUsuario);
            if (med != null) {
                med.setInventario(cantidad != null ? cantidad : 0);
                toUpdate.add(med);
            }
        }
        
        medicamentoRepository.saveAll(toUpdate);
        System.out.println("Saldos actualizados directamente en catálogo: " + toUpdate.size() + " registros.");
    }

    @org.springframework.transaction.annotation.Transactional
    public void importFromExternalData(List<MedicamentoImportDTO> items) {
        System.out.println("Iniciando sincronización avanzada (PLU + SEDE) de " + items.size() + " medicamentos...");
        
        // 1. Cargamos catálogo actual en memoria usando llave compuesta para no duplicar por sede
        List<Medicamento> currentCatalog = medicamentoRepository.findAll();
        java.util.Map<String, Medicamento> medMap = new java.util.HashMap<>();
        for (Medicamento m : currentCatalog) {
            if (m.getPlu() != null && m.getIdUsuario() != null) {
                // Llave única por sede: "PLU_IDUSUARIO"
                medMap.put(m.getPlu() + "_" + m.getIdUsuario(), m);
            }
        }

        java.util.List<Medicamento> toSave = new java.util.ArrayList<>();

        for (MedicamentoImportDTO item : items) {
            if (item.getPlu() == null || item.getPlu().isEmpty() || item.getIdUsuario() == null) continue;

            // Buscamos si ya existe ese PLU específico EN ESA SEDE
            String compositeKey = item.getPlu() + "_" + item.getIdUsuario();
            Medicamento med = medMap.get(compositeKey);
            
            if (med == null) {
                // Si no existe para esa sede, lo creamos
                med = new Medicamento();
                med.setPlu(item.getPlu());
                med.setIdUsuario(item.getIdUsuario()); // Vinculamos a la sede
                med.setEstadoDelConteo("no");
            }
            
            // Actualizamos solo los datos informativos
            if (item.getDescripcion() != null) med.setDescripcion(item.getDescripcion());
            if (item.getCodigoGenerico() != null) med.setCodigoGenerico(item.getCodigoGenerico());
            if (item.getLaboratorio() != null) med.setLaboratorio(item.getLaboratorio());
            
            // Inventario y Costos específicos de esta sede
            if (item.getInventario() != null) med.setInventario(item.getInventario());
            if (item.getCosto() != null) med.setCosto(item.getCosto());
            if (item.getCostoTotal() != null) med.setCostoTotal(item.getCostoTotal());
            
            toSave.add(med);
        }
        
        medicamentoRepository.saveAll(toSave);
        System.out.println("Sincronización multi-sede completada exitosamente.");
    }

    public Optional<Medicamento> getMedicamentoById(Integer id) {
        return medicamentoRepository.findById(id);
    }

    public Medicamento saveMedicamento(Medicamento medicamento) {
        return medicamentoRepository.save(medicamento);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetStatusByUsuario(Integer idUsuario) {
        List<Medicamento> medications = medicamentoRepository.findAll();
        List<Medicamento> toUpdate = new java.util.ArrayList<>();
        for (Medicamento m : medications) {
            if (m.getIdUsuario().equals(idUsuario) && "sí".equals(m.getEstadoDelConteo())) {
                m.setEstadoDelConteo("no");
                toUpdate.add(m);
            }
        }
        medicamentoRepository.saveAll(toUpdate);
        System.out.println("Ciclo reiniciado para usuario " + idUsuario + ". Se resetearon " + toUpdate.size() + " medicamentos.");
    }

    public void deleteMedicamento(Integer id) {
        medicamentoRepository.deleteById(id);
    }
}
