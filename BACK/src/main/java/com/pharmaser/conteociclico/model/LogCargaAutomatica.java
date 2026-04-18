package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "log_cargas_automaticas")
@Data
public class LogCargaAutomatica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "nombre_archivo")
    private String nombreArchivo;

    @Column(name = "registros_leidos")
    private Integer registrosLeidos;

    @Column(name = "registros_procesados")
    private Integer registrosProcesados;

    @Column(name = "estado")
    private String estado; // EXITOSO, ERROR, FALLIDO

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;
}
