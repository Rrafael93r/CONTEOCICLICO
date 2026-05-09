package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.SedeConfig;
import com.pharmaser.conteociclico.service.SedeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/sede-config")
public class SedeConfigController {

    @Autowired
    private SedeConfigService sedeConfigService;

    @GetMapping
    public List<SedeConfig> getAll() {
        return sedeConfigService.getAllConfigs();
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping
    public SedeConfig create(@RequestBody SedeConfig config) {
        return sedeConfigService.saveConfig(config);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/{id}")
    public SedeConfig update(@PathVariable Integer id, @RequestBody SedeConfig config) {
        return sedeConfigService.updateConfig(id, config);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        sedeConfigService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/sync")
    public ResponseEntity<String> sync() {
        int created = sedeConfigService.syncSedesFromUsers();
        return ResponseEntity.ok("Sincronización completada. Se crearon " + created + " nuevas configuraciones de sede.");
    }
}
