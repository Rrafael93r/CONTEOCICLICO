package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.service.CostoSedeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/costos-sede")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class CostoSedeController {

    @Autowired
    private CostoSedeService costoSedeService;

    @PostMapping("/importar")
    public ResponseEntity<?> importarCostos(@RequestParam("file") MultipartFile file) {
        try {
            String mensaje = costoSedeService.procesarArchivo(file);
            return ResponseEntity.ok(Map.of("mensaje", mensaje, "estado", "EXITOSO"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage(), "estado", "ERROR"));
        }
    }
}
