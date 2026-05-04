package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "maestra_medicamentos")
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class MaestraMedicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item")
    private Integer item;

    @Column(name = "plu", length = 50, unique = true)
    private String plu;

    @Column(name = "codigogenerico", length = 50)
    private String codigogenerico;

    @Column(name = "nombre", columnDefinition = "TEXT")
    private String nombre;

    @Column(name = "nombre_comercial", length = 255)
    private String nombreComercial;

    @Column(name = "laboratorio", length = 255)
    private String laboratorio;

    @Column(name = "forma_farmaceutica", length = 100)
    private String formaFarmaceutica;

    @Column(name = "concentracion", length = 100)
    private String concentracion;

    @Column(name = "concentracion2", length = 100)
    private String concentracion2;

    @Column(name = "unidad_concentracion", length = 50)
    private String unidadConcentracion;

    @Column(name = "unidad_contenido", length = 50)
    private String unidadContenido;

    @Column(name = "concentracion_agrupada", length = 100)
    private String concentracionAgrupada;

    @Column(name = "registro_sanitario", length = 100)
    private String registroSanitario;

    @Column(name = "cum", length = 100)
    private String cum;

    @Column(name = "contrato", length = 100)
    private String contrato;

    @Column(name = "unidad_medida", length = 50)
    private String unidadMedida;

    @Column(name = "fecha_inactivacion")
    private LocalDateTime fechaInactivacion;

    @Column(name = "usuario_inactivacion", length = 100)
    private String usuarioInactivacion;

    @Column(name = "rips_codigo", length = 50)
    private String ripsCodigo;

    @Column(name = "rips_unidad", length = 50)
    private String ripsUnidad;

    @Column(name = "fecha_carga", insertable = false, updatable = false)
    private LocalDateTime fechaCarga;

    @PrePersist
    protected void onCreate() {
        if (fechaCarga == null) {
            fechaCarga = LocalDateTime.now();
        }
    }
}
