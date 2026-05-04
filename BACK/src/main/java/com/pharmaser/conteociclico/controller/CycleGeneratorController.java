package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.service.CycleGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/detalle-conteo")
public class CycleGeneratorController {

    @Autowired
    private CycleGeneratorService cycleGeneratorService;

    @PostMapping("/generar-bloque/{idUsuario}")
    public ResponseEntity<List<Medicamento>> generarBloqueCiclico(
            @PathVariable Integer idUsuario,
            @RequestParam String fechaHoy,
            @RequestParam(required = false) Boolean manual,
            @RequestParam(required = false) Integer idAdmin) {
        try {
            LocalDate date = LocalDate.parse(fechaHoy);
            List<Medicamento> asignados = cycleGeneratorService.generarBloqueCiclico(idUsuario, date, manual, idAdmin);
            return ResponseEntity.ok(asignados);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
