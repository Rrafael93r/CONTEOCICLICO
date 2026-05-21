package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.LogCargaAutomatica;
import com.pharmaser.conteociclico.repository.LogCargaAutomaticaRepository;
import com.pharmaser.conteociclico.service.AutomatedImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs-carga")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_DE_INVENTARIO')")
public class LogCargaController {

    private static final Logger logger = LoggerFactory.getLogger(LogCargaController.class);

    @Autowired
    private LogCargaAutomaticaRepository logRepository;

    @Autowired
    private AutomatedImportService automatedImportService;

    /**
     * Executor dedicado para ejecutar la sincronización manual en background.
     * Limita a 1 hilo: si ya hay uno corriendo, la tarea siguiente se encola
     * pero no se ejecuta porque isRunning() lo evita.
     */
    private final ThreadPoolTaskExecutor executor;

    public LogCargaController() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);   // sin cola: si el hilo está ocupado, rechaza
        executor.setThreadNamePrefix("sync-manual-");
        executor.initialize();
    }

    @GetMapping
    public ResponseEntity<List<LogCargaAutomatica>> getLogs() {
        return ResponseEntity.ok(logRepository.findAllByOrderByFechaInicioDesc());
    }

    /**
     * Dispara una sincronización SFTP + procesamiento de forma asíncrona.
     *
     * FIX respecto a la versión anterior:
     * - Detecta si ya hay un ciclo en curso y responde 409 en ese caso.
     * - Usa un ThreadPoolTaskExecutor en lugar de new Thread() crudo.
     * - Los errores se loguean correctamente (no solo e.printStackTrace()).
     */
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/trigger-sync")
    public ResponseEntity<?> triggerManualSync() {

        if (automatedImportService.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "mensaje", "Ya hay una sincronización en progreso. " +
                                   "Por favor espere a que termine antes de iniciar otra.",
                        "enProceso", true
                    ));
        }

        try {
            executor.submit(() -> {
                try {
                    automatedImportService.scheduledBotSync();
                } catch (Exception e) {
                    logger.error("Error en sincronización manual disparada por usuario: {}", e.getMessage(), e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // El pool ya tiene una tarea (hilo ocupado y sin cola)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "mensaje", "La solicitud fue rechazada porque ya hay una sincronización en ejecución.",
                        "enProceso", true
                    ));
        }

        return ResponseEntity.ok(Map.of(
            "mensaje", "Sincronización manual iniciada en segundo plano. " +
                       "Revise la tabla de logs en unos instantes.",
            "enProceso", false
        ));
    }

    /**
     * Endpoint de consulta rápida: ¿está corriendo un ciclo ahora mismo?
     * Útil para que el frontend muestre un indicador de "procesando".
     */
    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> getEstado() {
        return ResponseEntity.ok(Map.of(
            "enProceso", automatedImportService.isRunning()
        ));
    }
}
