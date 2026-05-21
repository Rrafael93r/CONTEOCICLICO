package com.pharmaser.conteociclico.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Scheduler con pool de 3 hilos.
     *
     * Por defecto Spring usa 1 único hilo para TODOS los @Scheduled.
     * El proyecto tiene 4 tareas calendarizadas:
     *   - AutomatedImportService.processFiles()       (cada 5 min)
     *   - AutomatedImportService.scheduledBotSync()   (cada 30 min)
     *   - DailyCleanupTask.cleanupUncountedMedications() (23:59 diario)
     *   - MonthlyResetTask.resetMonthlyCycle()        (día 1 del mes)
     *
     * Con 1 hilo, si scheduledBotSync() tarda 10+ minutos procesando archivos
     * grandes, processFiles() acumula invocaciones en la cola y dispara 2-3
     * veces seguidas al liberarse el hilo. El guard isProcessing evita el doble
     * procesamiento, pero el queueing genera retrasos innecesarios.
     *
     * Con 3 hilos, las 4 tareas pueden ejecutarse sin bloquearse entre sí.
     * El guard isProcessing (AtomicBoolean) sigue siendo la barrera de seguridad
     * real contra ejecuciones concurrentes del mismo ciclo de importación.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
}
