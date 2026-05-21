package com.pharmaser.conteociclico.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class DailyCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(DailyCleanupTask.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Ejecutar todos los días a las 23:59:00 P.M.
     * Libera en un único UPDATE en lote todos los medicamentos que quedaron
     * asignados hoy pero nunca fueron contados (cantidad_contada IS NULL),
     * evitando el patrón N+1 que generaba una query por medicamento.
     */
    @Scheduled(cron = "0 59 23 * * ?")
    @Transactional
    public void cleanupUncountedMedications() {
        LocalDate hoy = LocalDate.now();
        logger.info("Iniciando Robot Nocturno (DailyCleanupTask) para la fecha: {}", hoy);

        int devueltos = jdbcTemplate.update(
            "UPDATE medicamento m " +
            "INNER JOIN detalleconteo d ON d.idmedicamento = m.id " +
            "SET m.estadodelconteo = 'no' " +
            "WHERE d.fecharegistro = ? " +
            "  AND d.cantidadcontada IS NULL " +
            "  AND LOWER(m.estadodelconteo) = 'sí'",
            hoy
        );

        logger.info("Robot Nocturno completado. {} medicamento(s) devueltos a la piscina de inventario.", devueltos);
    }
}
