package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.MaestraMedicamento;
import com.pharmaser.conteociclico.service.MaestraMedicamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maestra")
public class MaestraMedicamentoController {

    @Autowired
    private MaestraMedicamentoService service;

    @GetMapping
    public List<MaestraMedicamento> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaestraMedicamento> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/plu/{plu}")
    public ResponseEntity<MaestraMedicamento> getByPlu(@PathVariable String plu) {
        return service.findByPlu(plu)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public MaestraMedicamento create(@RequestBody MaestraMedicamento maestraMedicamento) {
        return service.save(maestraMedicamento);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaestraMedicamento> update(@PathVariable Long id, @RequestBody MaestraMedicamento maestraMedicamento) {
        return service.findById(id)
                .map(existing -> {
                    maestraMedicamento.setId(id);
                    return ResponseEntity.ok(service.save(maestraMedicamento));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<String> bulkCreate(@RequestBody List<MaestraMedicamento> lista) {
        service.bulkUpsert(lista);
        return ResponseEntity.ok("Maestra de medicamentos actualizada exitosamente (" + lista.size() + " registros)");
    }

    @PostMapping("/importar")
    public ResponseEntity<Map<String, Object>> importar(@RequestParam("file") MultipartFile file) {
        // En un entorno real, obtendríamos el usuario del contexto de seguridad
        String usuario = "ADMIN"; 
        Map<String, Object> result = service.importarArchivo(file, usuario);
        return ResponseEntity.ok(result);
    }
}
