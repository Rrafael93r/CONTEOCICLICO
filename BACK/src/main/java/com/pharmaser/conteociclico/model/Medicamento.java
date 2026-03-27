package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "medicamento")
@Data
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plu")
    private String plu;

    @Column(name = "codigogenerico")
    private String codigoGenerico;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "laboratorio", columnDefinition = "TEXT")
    private String laboratorio;

    @Column(name = "idusuario")
    private Integer idUsuario;

    @ManyToOne
    @JoinColumn(name = "idusuario", insertable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "estadodelconteo")
    private String estadoDelConteo;

    @Column(name = "inventario")
    private Integer inventario;

    @Column(name = "costo")
    private Double costo;

    @Column(name = "costototal")
    private Double costoTotal;
}
