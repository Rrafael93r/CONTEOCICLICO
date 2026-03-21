package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.Inventario;
import com.pharmaser.conteociclico.service.InventarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    @Autowired
    private InventarioService inventarioService;

    @GetMapping
    public List<Inventario> getAll() {
        return inventarioService.getAllInventarios();
    }

    @PostMapping
    public Inventario create(@RequestBody Inventario inventario) {
        return inventarioService.saveInventario(inventario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        inventarioService.deleteInventario(id);
        return ResponseEntity.noContent().build();
    }
}
