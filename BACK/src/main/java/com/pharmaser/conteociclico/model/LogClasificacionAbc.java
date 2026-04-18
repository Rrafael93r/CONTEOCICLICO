package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "log_clasificacion_abc")
@Data
public class LogClasificacionAbc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_ejecucion")
    private LocalDateTime fechaEjecucion;

    @Column(name = "version_reglas")
    private Integer versionReglas;

    @Column(name = "snapshot_config", columnDefinition = "TEXT")
    private String snapshotConfig;

    @Column(name = "registros_procesados")
    private Integer registrosProcesados;

    @Column(name = "count_a")
    private Integer countA;

    @Column(name = "count_b")
    private Integer countB;

    @Column(name = "count_c")
    private Integer countC;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(name = "estado", length = 20)
    private String estado; // EXITO, ERROR

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;
}
