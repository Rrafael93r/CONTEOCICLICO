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

    public List<Medicamento> getAllMedicamentos() {
        return medicamentoRepository.findAll();
    }

    public void importFromExternalData(List<MedicamentoImportDTO> items) {
        for (MedicamentoImportDTO item : items) {
            if (item.getPlu() == null || item.getPlu().isEmpty()) continue;

            Medicamento med = medicamentoRepository.findByPlu(item.getPlu())
                    .orElse(new Medicamento());
            
            med.setPlu(item.getPlu());
            if (item.getDescripcion() != null) med.setDescripcion(item.getDescripcion());
            if (item.getCodigoGenerico() != null) med.setCodigoGenerico(item.getCodigoGenerico());
            if (item.getLaboratorio() != null) med.setLaboratorio(item.getLaboratorio());
            
            // Por defecto, si es nuevo, el estado del conteo es "no"
            if (med.getId() == null) {
                med.setEstadoDelConteo("no");
            }
            
            medicamentoRepository.save(med);
        }
    }

    public Optional<Medicamento> getMedicamentoById(Integer id) {
        return medicamentoRepository.findById(id);
    }

    public Medicamento saveMedicamento(Medicamento medicamento) {
        return medicamentoRepository.save(medicamento);
    }

    public void deleteMedicamento(Integer id) {
        medicamentoRepository.deleteById(id);
    }
}
