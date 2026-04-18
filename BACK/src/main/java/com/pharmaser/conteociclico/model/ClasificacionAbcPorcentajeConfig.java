package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "clasificacion_abc_porcentaje_config")
@Data
public class ClasificacionAbcPorcentajeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tipo", length = 5, nullable = false, unique = true)
    private String tipo; // A, B, C

    @Column(name = "porcentaje_max", nullable = false)
    private Double porcentajeMax; // e.g., 80.0, 95.0, 100.0

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
