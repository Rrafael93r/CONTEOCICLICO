package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "medicamento")
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plu")
    private String plu;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "idusuario")
    private Integer idUsuario;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario", insertable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "estado_conteo_mensual")
    private Integer estadoConteoMensual = 0; // 0: No contado, 1: Primer conteo, 2: Segundo conteo (Solo Tipo A)

    @Column(name = "contado_mes_anterior")
    private Boolean contadoMesAnterior = false;

    @Column(name = "estadodelconteo")
    private String estadoDelConteo;

    @Column(name = "inventario")
    private Integer inventario;

    @Column(name = "costo")
    private Double costo;

    @Column(name = "costototal")
    private Double costoTotal;

    @Column(name = "tipomolecula")
    private String tipomolecula;

    @Column(name = "ciclosmes", columnDefinition = "INT DEFAULT 0")
    private Integer ciclosmes = 0;

    @Column(name = "fecha_clasificacion")
    private java.time.LocalDateTime fechaClasificacion;


    @Column(name = "fecha_actualizacion")
    private java.time.LocalDateTime fechaActualizacion;

    @Column(name = "fecha_ultimo_conteo")
    private java.time.LocalDateTime fechaUltimoConteo;

    @Column(name = "codigogenerico")
    private String codigogenerico;

}
