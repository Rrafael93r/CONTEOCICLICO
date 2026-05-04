package com.pharmaser.conteociclico.schedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MonthlyResetTask {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Ejecuta a las 00:00:00 el día 1 de cada mes
    @Scheduled(cron = "0 0 0 1 * ?") // 1st day of every month at midnight
    @Transactional
    public void resetMonthlyCycle() {
        // Guardar estado histórico para rotación
        jdbcTemplate.execute("UPDATE medicamento SET contado_mes_anterior = (estado_conteo_mensual >= 1)");
        
        // Resetear para el nuevo mes
        jdbcTemplate.execute("UPDATE medicamento SET estado_conteo_mensual = 0, estadodelconteo = 'no'");
    }
}
