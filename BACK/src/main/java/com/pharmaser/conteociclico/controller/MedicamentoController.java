package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.dto.MedicamentoImportDTO;
import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.service.MedicamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/medicamento")
public class MedicamentoController {

    @Autowired
    private MedicamentoService medicamentoService;

    @GetMapping
    public List<Medicamento> getAll() {
        return medicamentoService.getAllMedicamentos();
    }

    @PostMapping("/bulk")
    public ResponseEntity<String> bulkImport(@RequestBody List<MedicamentoImportDTO> items) {
        medicamentoService.importFromExternalData(items);
        return ResponseEntity.ok("Catálogo actualizado exitosamente");
    }

    @PostMapping("/bulk-inventory")
    public ResponseEntity<String> bulkInventory(@RequestBody List<java.util.Map<String, Object>> items) {
        medicamentoService.bulkUpdateInventory(items);
        return ResponseEntity.ok("Saldos actualizados exitosamente");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Medicamento> getById(@PathVariable Integer id) {
        return medicamentoService.getMedicamentoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Medicamento create(@RequestBody Medicamento medicamento) {
        return medicamentoService.saveMedicamento(medicamento);
    }

    @PutMapping("/{id}")
    public Medicamento update(@PathVariable Integer id, @RequestBody Medicamento medicamento) {
        medicamento.setId(id);
        return medicamentoService.saveMedicamento(medicamento);
    }

    @PostMapping("/reset-cycle/{idUsuario}")
    public ResponseEntity<String> resetCycle(@PathVariable Integer idUsuario) {
        medicamentoService.resetStatusByUsuario(idUsuario);
        return ResponseEntity.ok("Ciclo reiniciado correctamente para el usuario " + idUsuario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        medicamentoService.deleteMedicamento(id);
        return ResponseEntity.noContent().build();
    }
}
