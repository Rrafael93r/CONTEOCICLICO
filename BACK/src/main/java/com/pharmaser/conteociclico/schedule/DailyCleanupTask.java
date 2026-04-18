package com.pharmaser.conteociclico.schedule;

import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.repository.DetalleConteoRepository;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class DailyCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(DailyCleanupTask.class);

    @Autowired
    private DetalleConteoRepository detalleConteoRepository;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    /**
     * Ejecutar todos los días a las 23:59:00 P.M.
     * Cron: Segundos Minutos Horas DiaDeMes Mes DiaDeSemana
     */
    @Scheduled(cron = "0 59 23 * * ?")
    public void cleanupUncountedMedications() {
        LocalDate hoy = LocalDate.now();
        logger.info("Iniciando Robot Nocturno de limpieza (DailyCleanupTask) para la fecha: " + hoy);

        List<DetalleConteo> asignadosHoy = detalleConteoRepository.findByFechaRegistro(hoy);
        int devueltos = 0;

        for (DetalleConteo d : asignadosHoy) {
            // Si el farmaceuta nunca registró una cantidad final, asumimos el registro como huérfano.
            if (d.getCantidadContada() == null) {
                Medicamento m = d.getMedicamento();
                // Liberar el estado en la tabla de inventario general
                if (m != null && "sí".equalsIgnoreCase(m.getEstadoDelConteo())) {
                    m.setEstadoDelConteo("no");
                    medicamentoRepository.save(m);
                    devueltos++;
                }
            }
        }
        
        logger.info("El Robot Nocturno terminó con éxito. Total de medicamentos devueltos a la piscina de inventario: " + devueltos);
    }
}
