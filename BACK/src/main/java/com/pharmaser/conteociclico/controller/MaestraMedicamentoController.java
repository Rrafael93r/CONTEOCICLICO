package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.MaestraMedicamento;
import com.pharmaser.conteociclico.service.MaestraMedicamentoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_DE_INVENTARIO')")
    @PostMapping
    public MaestraMedicamento create(@RequestBody MaestraMedicamento maestraMedicamento) {
        return service.save(maestraMedicamento);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_DE_INVENTARIO')")
    @PutMapping("/{id}")
    public ResponseEntity<MaestraMedicamento> update(@PathVariable Long id, @RequestBody MaestraMedicamento maestraMedicamento) {
        return service.findById(id)
                .map(existing -> {
                    maestraMedicamento.setId(id);
                    return ResponseEntity.ok(service.save(maestraMedicamento));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'API')")
    @PostMapping("/bulk")
    public ResponseEntity<String> bulkCreate(@RequestBody List<MaestraMedicamento> lista) {
        service.bulkUpsert(lista);
        return ResponseEntity.ok("Maestra de medicamentos actualizada exitosamente (" + lista.size() + " registros)");
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/importar")
    public ResponseEntity<Map<String, Object>> importar(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = (auth != null && auth.getName() != null) ? auth.getName() : "SISTEMA";
        Map<String, Object> result = service.importarArchivo(file, usuario);
        return ResponseEntity.ok(result);
    }
}
