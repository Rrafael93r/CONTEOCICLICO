package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.dto.MedicamentoImportDTO;
import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import com.pharmaser.conteociclico.service.CycleGeneratorService;
import com.pharmaser.conteociclico.service.MedicamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/medicamento")
public class MedicamentoController {

    @Autowired
    private MedicamentoService medicamentoService;

    @Autowired
    private CycleGeneratorService cycleGeneratorService;

    @Autowired
    private UsuarioRepository usuarioRepository;


    @GetMapping
    public List<Medicamento> getAll(@RequestParam(required = false) Integer idUsuario) {
        if (idUsuario != null) {
            return medicamentoService.getMedicamentosByUsuario(idUsuario);
        }
        return medicamentoService.getAllMedicamentos();
    }

    @PostMapping("/bulk")
    public ResponseEntity<String> bulkImport(@RequestBody List<MedicamentoImportDTO> items) {
        medicamentoService.importFromExternalData(items);
        return ResponseEntity.ok("Catálogo actualizado exitosamente");
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncInventory(@RequestBody List<java.util.Map<String, String>> items) {
        StringBuilder logs = new StringBuilder();
        java.util.Map<String, Integer> userSedeMap = medicamentoService.buildUserSedeMap();
        List<MedicamentoImportDTO> validItems = medicamentoService.normalizeAndValidate(items, userSedeMap, logs);

        medicamentoService.importFromExternalData(validItems);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("mensaje", "Procesamiento completado");
        response.put("leidos", items.size());
        response.put("procesados", validItems.size());
        response.put("logs", logs.toString());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/bulk-status")
    public ResponseEntity<String> bulkUpdateStatus(@RequestBody List<Integer> ids) {
        medicamentoService.markAsCounted(ids);
        return ResponseEntity.ok("Estados actualizados exitosamente");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Medicamento> getById(@PathVariable int id) {
        return medicamentoService.getMedicamentoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Medicamento create(@RequestBody Medicamento medicamento) {
        return medicamentoService.saveMedicamento(medicamento);
    }

    @PutMapping("/{id}")
    public Medicamento update(@PathVariable int id, @RequestBody Medicamento medicamento) {
        medicamento.setId(id);
        return medicamentoService.saveMedicamento(medicamento);
    }

    @PostMapping("/reset-cycle/{idUsuario}")
    public ResponseEntity<String> resetCycle(@PathVariable int idUsuario) {
        medicamentoService.resetStatusByUsuario(idUsuario);
        return ResponseEntity.ok("Ciclo reiniciado correctamente para el usuario " + idUsuario);
    }

    @GetMapping("/asignar-diario/{idUsuario}")
    public List<Medicamento> getAsignacionDiaria(@PathVariable int idUsuario) {
        return cycleGeneratorService.obtenerBloqueDiarioDinamico(idUsuario);
    }

    @PostMapping("/admin/bloque-extra/{idUsuario}")
    public ResponseEntity<?> asignarBloqueExtra(@PathVariable int idUsuario) {
        Usuario user = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Validar si ya tiene un permiso activo hoy
        if (user.getFechaBloqueExtra() != null && user.getFechaBloqueExtra().equals(LocalDate.now())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Ya se ha autorizado un bloque extra hoy para este usuario.");
        }

        // REGLA: Solo si el bloque diario está terminado al 100%
        if (!cycleGeneratorService.isDailyBlockFinished(idUsuario)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body("No se puede asignar bloque extra: El usuario aún no ha terminado su bloque diario obligatorio.");
        }

        user.setFechaBloqueExtra(LocalDate.now());
        usuarioRepository.save(user);

        return ResponseEntity.ok("Bloque extra autorizado con éxito. El usuario recibirá las nuevas moléculas al refrescar su lista.");
    }

    @PostMapping("/reset-cycle/{idUsuario}/{tipo}")
    public ResponseEntity<String> resetCycleByTipo(@PathVariable int idUsuario, @PathVariable String tipo) {
        medicamentoService.resetStatusByUsuarioAndTipo(idUsuario, tipo);
        return ResponseEntity.ok("Ciclo para " + tipo + " reiniciado.");
    }

    @PostMapping("/reset-all-cycles")
    public ResponseEntity<String> resetAllCycles() {
        medicamentoService.resetAllStatus();
        return ResponseEntity.ok("Ciclos de todos los usuarios reiniciados correctamente");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        medicamentoService.deleteMedicamento(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reclassify-all")
    public ResponseEntity<String> reclassifyAll() {
        try {
            medicamentoService.reclassifyAllMedicamentos();
            return ResponseEntity.ok("Reclasificación masiva completada con éxito bajó lógica de Pareto.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error durante la reclasificación: " + e.getMessage());
        }
    }

    @GetMapping("/summary")
    public List<com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO> getSummary() {
        return medicamentoService.getSedesSummary();
    }

    @GetMapping("/dashboard-stats")
    public java.util.Map<String, Object> getDashboardStats() {
        return medicamentoService.getGlobalStats();
    }

    @GetMapping("/search")
    public List<Medicamento> search(@RequestParam String q, @RequestParam(defaultValue = "50") int limit) {
        return medicamentoService.searchMedicamentos(q, limit);
    }
}
