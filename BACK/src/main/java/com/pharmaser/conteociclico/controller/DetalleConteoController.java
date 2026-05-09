package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.service.DetalleConteoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.NonNull;
import java.util.List;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/detalle-conteo")
public class DetalleConteoController {

    @Autowired
    private DetalleConteoService detalleConteoService;

    @GetMapping
    public List<DetalleConteo> getAll(@RequestParam(required = false) Integer idUsuario, 
                                     @RequestParam(required = false) String sede,
                                     @RequestParam(required = false) String fecha,
                                     @RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate) {
        
        LocalDate reqDate = (fecha != null && !fecha.isEmpty()) ? LocalDate.parse(fecha) : LocalDate.now();

        // Logica de rangos para reportes
        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            if (sede != null && !sede.isEmpty()) {
                return detalleConteoService.getDetallesBySedeYRango(sede, start, end);
            }
            if (idUsuario != null) {
                return detalleConteoService.getDetallesByUsuarioYRango(idUsuario, start, end);
            }
            return detalleConteoService.getDetallesByRango(start, end);
        }

        // Caso por defecto
        if (sede != null && !sede.isEmpty()) {
            return detalleConteoService.getDetallesBySedeYFecha(sede, reqDate);
        }
        if (idUsuario != null) {
            return detalleConteoService.getDetallesByUsuarioYFecha(idUsuario, reqDate);
        }
        
        return detalleConteoService.getDetallesByFecha(reqDate);
    }

    @PostMapping
    public DetalleConteo create(@RequestBody @NonNull DetalleConteo detalle) {
        return detalleConteoService.saveDetalle(detalle);
    }

    @PostMapping("/bulk")
    public List<DetalleConteo> bulkCreate(@RequestBody List<DetalleConteo> detalles) {
        return detalleConteoService.saveAllDetalles(detalles);
    }

    @PutMapping("/bulk")
    public List<DetalleConteo> bulkUpdate(@RequestBody List<DetalleConteo> detalles) {
        return detalleConteoService.saveAllDetalles(detalles);
    }

    @PutMapping("/{id}")
    public DetalleConteo update(@PathVariable Integer id, @RequestBody DetalleConteo detalle) {
        detalle.setId(id);
        return detalleConteoService.saveDetalle(detalle);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_DE_INVENTARIO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @org.springframework.lang.NonNull Integer id) {
        detalleConteoService.deleteDetalle(id);
        return ResponseEntity.noContent().build();
    }
}
