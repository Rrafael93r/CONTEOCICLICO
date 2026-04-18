package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalTime;

@Entity
@Table(name = "sede_config")
@Data
public class SedeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo_sede", length = 10, nullable = false, unique = true)
    private String codigoSede;

    @Column(name = "nombre", length = 100)
    private String nombre;

    @Column(name = "numero_conteo")
    private Integer numeroConteo;

    @Column(name = "tipo_conteo", length = 20)
    private String tipoConteo;

    @Column(name = "operacion_inicio")
    private LocalTime operacionInicio;

    @Column(name = "operacion_fin")
    private LocalTime operacionFin;

    @Column(name = "activo")
    private Integer activo = 1;
}
