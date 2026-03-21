package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.service.DetalleConteoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/detalle-conteo")
public class DetalleConteoController {

    @Autowired
    private DetalleConteoService detalleConteoService;

    @GetMapping
    public List<DetalleConteo> getAll() {
        return detalleConteoService.getAllDetalles();
    }

    @PostMapping
    public DetalleConteo create(@RequestBody DetalleConteo detalle) {
        return detalleConteoService.saveDetalle(detalle);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        detalleConteoService.deleteDetalle(id);
        return ResponseEntity.noContent().build();
    }
}
