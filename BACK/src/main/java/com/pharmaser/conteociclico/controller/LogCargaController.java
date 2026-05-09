package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.LogCargaAutomatica;
import com.pharmaser.conteociclico.repository.LogCargaAutomaticaRepository;
import com.pharmaser.conteociclico.service.AutomatedImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs-carga")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_DE_INVENTARIO')")
public class LogCargaController {

    @Autowired
    private LogCargaAutomaticaRepository logRepository;

    @Autowired
    private AutomatedImportService automatedImportService;

    @GetMapping
    public ResponseEntity<List<LogCargaAutomatica>> getLogs() {
        return ResponseEntity.ok(logRepository.findAllByOrderByFechaInicioDesc());
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/trigger-sync")
    public ResponseEntity<?> triggerManualSync() {
        // Ejecutamos en un nuevo hilo para que no bloquee la petición,
        // ya que la sincronización puede tardar.
        new Thread(() -> {
            try {
                automatedImportService.scheduledBotSync();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return ResponseEntity.ok(Map.of("mensaje", "Sincronización manual iniciada. Por favor revise los logs en unos minutos."));
    }
}
