package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.service.DetalleConteoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/detalle-conteo")
public class DetalleConteoController {

    @Autowired
    private DetalleConteoService detalleConteoService;

    @GetMapping
    public List<DetalleConteo> getAll(@RequestParam(required = false) Integer idUsuario, 
                                     @RequestParam(required = false) String fecha) {
        if (idUsuario != null && fecha != null) {
            return detalleConteoService.getDetallesByUsuarioYFecha(idUsuario, LocalDate.parse(fecha));
        }
        return detalleConteoService.getAllDetalles();
    }

    @PostMapping
    public DetalleConteo create(@RequestBody DetalleConteo detalle) {
        return detalleConteoService.saveDetalle(detalle);
    }

    @PutMapping("/{id}")
    public DetalleConteo update(@PathVariable Integer id, @RequestBody DetalleConteo detalle) {
        detalle.setId(id);
        return detalleConteoService.saveDetalle(detalle);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        detalleConteoService.deleteDetalle(id);
        return ResponseEntity.noContent().build();
    }
}
