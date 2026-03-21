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

    @Column(name = "ceco")
    private String ceco;

    @Column(name = "estadodelconteo")
    private String estadoDelConteo;
}
